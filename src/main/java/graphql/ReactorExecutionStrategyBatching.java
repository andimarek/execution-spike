package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import graphql.result.ExecutionResultNode;
import graphql.result.ExecutionResultNodeZipper;
import graphql.result.MultiZipper;
import graphql.result.ObjectExecutionResultNode;
import graphql.result.ResultNodesUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.result.ObjectExecutionResultNode.RootExecutionResultNode;
import static graphql.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class ReactorExecutionStrategyBatching {

    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public ReactorExecutionStrategyBatching(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    public Mono<RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        Mono<RootExecutionResultNode> rootMono = fetchSubSelection(fieldSubSelection).map(RootExecutionResultNode::new);

        return rootMono.flatMap(rootNode -> {
            MultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNode);
            return nextStep(unresolvedNodes);
        }).map(finalZipper -> finalZipper.toRootNode())
                .cast(RootExecutionResultNode.class);
    }


    private Mono<Map<String, ExecutionResultNode>> fetchSubSelection(FieldSubSelection fieldSubSelection) {
        Flux<FetchedValueAnalysis> fetchedValueAnalysisFlux = fetchAndAnalyze(fieldSubSelection);
        return fetchedValueAnalysisFluxToNodes(fetchedValueAnalysisFlux);
    }

    private Mono<Map<String, ExecutionResultNode>> fetchedValueAnalysisFluxToNodes(Flux<FetchedValueAnalysis> fetchedValueAnalysisFlux) {
        Flux<Tuple2<String, ExecutionResultNode>> tuplesFlux = fetchedValueAnalysisFlux
                .map(fetchedValueAnalysis -> Tuples.of(fetchedValueAnalysis.getName(), resultNodesCreator.createResultNode(fetchedValueAnalysis)));
        return tuplesToMap(tuplesFlux);
    }


    private <U> Mono<Map<String, U>> tuplesToMap(Flux<Tuple2<String, U>> tuplesFlux) {
        return tuplesFlux.reduce(new LinkedHashMap<>(), (acc, tuple) -> {
            U value = tuple.getT2();
            acc.put(tuple.getT1(), value);
            return acc;
        });
    }

    private Mono<MultiZipper> nextStep(MultiZipper multizipper) {
        MultiZipper nextUnresolvedNodes = ResultNodesUtil.getUnresolvedNodes(multizipper.toRootNode());
        if (nextUnresolvedNodes.getZippers().size() == 0) {
            return Mono.just(nextUnresolvedNodes);
        }
        List<MultiZipper> groups = groupNodesIntoBatches(nextUnresolvedNodes);
        return nextStepImpl(groups).flatMap(this::nextStep);
    }

    // all multizipper have the same root
    private Mono<MultiZipper> nextStepImpl(List<MultiZipper> unresolvedNodes) {
        Assert.assertNotEmpty(unresolvedNodes, "unresolvedNodes can't be empty");
        ExecutionResultNode commonRoot = unresolvedNodes.get(0).getCommonRoot();
        Mono<List<List<ExecutionResultNodeZipper>>> listListMono = Flux.fromIterable(unresolvedNodes)
                .flatMap(multiZipper -> fetchAndAnalyze(multiZipper.getZippers())).collectList();

        return Common.flastList(listListMono)
                .map(zippers -> new MultiZipper(commonRoot, zippers));

    }

    private List<MultiZipper> groupNodesIntoBatches(MultiZipper unresolvedZipper) {
        Map<Map<String, List<Field>>, List<ExecutionResultNodeZipper>> zipperBySubSelection = unresolvedZipper.getZippers().stream()
                .collect(groupingBy(executionResultNodeZipper -> executionResultNodeZipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection().getFields()));

        return zipperBySubSelection
                .entrySet()
                .stream()
                .map(entry -> new MultiZipper(unresolvedZipper.getCommonRoot(), entry.getValue()))
                .collect(Collectors.toList());
    }

    //constrain: all fieldSubSelections have the same fields
    private Mono<List<ExecutionResultNodeZipper>> fetchAndAnalyze(List<ExecutionResultNodeZipper> unresolvedNodes) {
        Assert.assertTrue(unresolvedNodes.size() > 0, "unresolvedNodes can't be empty");

        List<FieldSubSelection> fieldSubSelections = unresolvedNodes.stream()
                .map(zipper -> zipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection())
                .collect(Collectors.toList());
        List<Object> sources = fieldSubSelections.stream().map(fieldSubSelection -> fieldSubSelection.getSource()).collect(Collectors.toList());

        // each field in the subSelection has n sources as input
        List<Mono<List<FetchedValueAnalysis>>> fetchedValues = fieldSubSelections
                .get(0)
                .getFields()
                .entrySet()
                .stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();

                    List<ExecutionStepInfo> newExecutionStepInfos = fieldSubSelections.stream().map(executionResultNode -> {
                        return executionInfoFactory.newExecutionStepInfoForSubField(sameFields, executionResultNode.getExecutionStepInfo());
                    }).collect(Collectors.toList());

                    Mono<List<FetchedValueAnalysis>> fetchedValueAnalyzis = valueFetcher
                            .fetchBatchedValues(sources, sameFields, newExecutionStepInfos)
                            .map(fetchValue -> analyseValues(fetchValue, name, sameFields, newExecutionStepInfos));
                    return fetchedValueAnalyzis;
                })
                .collect(toList());

        return Flux.merge(fetchedValues).collectList().onErrorMap(throwable -> {
            throwable.printStackTrace();
            return throwable;
        }).map(fetchedValuesMatrix -> {

            List<ExecutionResultNodeZipper> result = new ArrayList<>();
            List<List<FetchedValueAnalysis>> newChildsPerNode = Common.transposeMatrix(fetchedValuesMatrix);

            for (int i = 0; i < newChildsPerNode.size(); i++) {
                ExecutionResultNodeZipper unresolvedNodeZipper = unresolvedNodes.get(i);
                List<FetchedValueAnalysis> fetchedValuesForNode = newChildsPerNode.get(i);
                ExecutionResultNodeZipper resolvedZipper = resolvedZipper(unresolvedNodeZipper, fetchedValuesForNode);
                result.add(resolvedZipper);
            }
            return result;
        });
    }

    private ExecutionResultNodeZipper resolvedZipper(ExecutionResultNodeZipper unresolvedNodeZipper, List<FetchedValueAnalysis> fetchedValuesForNode) {
        UnresolvedObjectResultNode unresolvedNode = (UnresolvedObjectResultNode) unresolvedNodeZipper.getCurNode();
        Map<String, ExecutionResultNode> newChildren = fetchedValueAnalysisToNodes(fetchedValuesForNode);
        ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
        return unresolvedNodeZipper.withNode(newNode);
    }

    private Map<String, ExecutionResultNode> fetchedValueAnalysisToNodes(List<FetchedValueAnalysis> fetchedValueAnalysisFlux) {
        Map<String, ExecutionResultNode> result = new LinkedHashMap<>();
        fetchedValueAnalysisFlux.forEach(fetchedValueAnalysis -> {
            result.put(fetchedValueAnalysis.getName(), resultNodesCreator.createResultNode(fetchedValueAnalysis));
        });
        return result;
    }


    // only used for the root sub selection atm
    private Flux<FetchedValueAnalysis> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fieldSubSelection.getExecutionStepInfo());
                    return valueFetcher
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
                })
                .collect(toList());

        return Flux.merge(fetchedValues);
    }

    // only used for the root sub selection atm
    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }


    private List<FetchedValueAnalysis> analyseValues(List<FetchedValue> fetchedValues, String name, List<Field> field, List<ExecutionStepInfo> executionInfos) {
        List<FetchedValueAnalysis> result = new ArrayList<>();
        for (int i = 0; i < fetchedValues.size(); i++) {
            FetchedValue fetchedValue = fetchedValues.get(i);
            ExecutionStepInfo executionStepInfo = executionInfos.get(i);
            FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionStepInfo);
            fetchedValueAnalysis.setFetchedValue(fetchedValue);
            result.add(fetchedValueAnalysis);
        }
        return result;
    }

}
