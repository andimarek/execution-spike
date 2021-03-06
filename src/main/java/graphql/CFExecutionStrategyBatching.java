package graphql;

import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import graphql.result.ExecutionResultNode;
import graphql.result.ExecutionResultNodeZipper;
import graphql.result.MultiZipper;
import graphql.result.ObjectExecutionResultNode;
import graphql.result.ResultNodesUtil;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static graphql.result.ObjectExecutionResultNode.RootExecutionResultNode;
import static graphql.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class CFExecutionStrategyBatching {

    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcherCF valueFetcher;
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public CFExecutionStrategyBatching(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcherCF(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    public CompletableFuture<RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        CompletableFuture<RootExecutionResultNode> rootMono = fetchSubSelection(fieldSubSelection).thenApply(RootExecutionResultNode::new);

        return rootMono
                .thenCompose(rootNode -> {
                    MultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNode);
                    return nextStep(unresolvedNodes);
                })
                .thenApply(finalZipper -> finalZipper.toRootNode())
                .thenApply(RootExecutionResultNode.class::cast);
    }


    private CompletableFuture<Map<String, ExecutionResultNode>> fetchSubSelection(FieldSubSelection fieldSubSelection) {
        CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux = fetchAndAnalyze(fieldSubSelection);
        return fetchedValueAnalysisFluxToNodes(fetchedValueAnalysisFlux);
    }

    private CompletableFuture<Map<String, ExecutionResultNode>> fetchedValueAnalysisFluxToNodes(CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux) {
        CompletableFuture<List<Tuple2<String, ExecutionResultNode>>> tuplesList = Async2.map(fetchedValueAnalysisFlux,
                fetchedValueAnalysis -> Tuples.of(fetchedValueAnalysis.getName(), resultNodesCreator.createResultNode(fetchedValueAnalysis)));
        return tuplesToMap(tuplesList);
    }


    private <U> CompletableFuture<Map<String, U>> tuplesToMap(CompletableFuture<List<Tuple2<String, U>>> tuplesFlux) {
        return Async2.reduce(tuplesFlux, new LinkedHashMap<>(), (acc, tuple) -> {
            U value = tuple.getT2();
            acc.put(tuple.getT1(), value);
            return acc;
        });
    }

    private CompletableFuture<MultiZipper> nextStep(MultiZipper multizipper) {
        MultiZipper nextUnresolvedNodes = ResultNodesUtil.getUnresolvedNodes(multizipper.toRootNode());
        if (nextUnresolvedNodes.getZippers().size() == 0) {
            return CompletableFuture.completedFuture(nextUnresolvedNodes);
        }
        List<MultiZipper> groups = groupNodesIntoBatches(nextUnresolvedNodes);
        return nextStepImpl(groups).thenCompose(this::nextStep);
    }

    // all multizipper have the same root
    private CompletableFuture<MultiZipper> nextStepImpl(List<MultiZipper> unresolvedNodes) {
        Assert.assertNotEmpty(unresolvedNodes, "unresolvedNodes can't be empty");
        ExecutionResultNode commonRoot = unresolvedNodes.get(0).getCommonRoot();

        CompletableFuture<List<List<ExecutionResultNodeZipper>>> listListCF = Async2.flatMap(unresolvedNodes,
                multiZipper -> fetchAndAnalyze(multiZipper.getZippers()));

        return Common.flatList(listListCF)
                .thenApply(zippers -> new MultiZipper(commonRoot, zippers));

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
    private CompletableFuture<List<ExecutionResultNodeZipper>> fetchAndAnalyze(List<ExecutionResultNodeZipper> unresolvedNodes) {
        Assert.assertTrue(unresolvedNodes.size() > 0, "unresolvedNodes can't be empty");

        List<FieldSubSelection> fieldSubSelections = unresolvedNodes.stream()
                .map(zipper -> zipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection())
                .collect(Collectors.toList());
        List<Object> sources = fieldSubSelections.stream().map(fieldSubSelection -> fieldSubSelection.getSource()).collect(Collectors.toList());

        // each field in the subSelection has n sources as input
        List<CompletableFuture<List<FetchedValueAnalysis>>> fetchedValues = fieldSubSelections
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

                    CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalyzis = valueFetcher
                            .fetchBatchedValues(sources, sameFields, newExecutionStepInfos)
                            .thenApply(fetchValue -> analyseValues(fetchValue, name, sameFields, newExecutionStepInfos));
                    return fetchedValueAnalyzis;
                })
                .collect(toList());

        return Async.each(fetchedValues).thenApply(fetchedValuesMatrix -> {
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
    private CompletableFuture<List<FetchedValueAnalysis>> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fieldSubSelection.getExecutionStepInfo());
                    return valueFetcher
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
                            .thenApply(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
                })
                .collect(toList());

        return Async.each(fetchedValues);
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
