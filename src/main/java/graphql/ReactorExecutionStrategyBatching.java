package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.language.Field;
import graphql.result.ExecutionResultNode;
import graphql.result.ExecutionResultNodeZipper;
import graphql.result.ResultNodesUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReactorExecutionStrategyBatching {

    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public ReactorExecutionStrategyBatching(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    public Mono<ExecutionResultNode.RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        return executeSubSelection(fieldSubSelection).map(ExecutionResultNode.RootExecutionResultNode::new);
    }

    public Mono<Map<String, ExecutionResultNode>> executeSubSelection(FieldSubSelection fieldSubSelection) {
        Mono<Map<String, ExecutionResultNode>> nextLevelNodes = fetchAndAnalyze(fieldSubSelection)
                .flatMap(fetchedValueAnalysis -> Mono.zip(Mono.just(fetchedValueAnalysis), createResultNode(fetchedValueAnalysis)))
                .reduce(new LinkedHashMap<>(), (acc, tuple) -> {
                    FetchedValueAnalysis fetchedValueAnalysis = tuple.getT1();
                    ExecutionResultNode executionResultNode = tuple.getT2();
                    acc.put(fetchedValueAnalysis.getName(), executionResultNode);
                    return acc;
                });
        nextLevelNodes.map(stringExecutionResultNodeMap -> {
            // list of root nodes
            Collection<ExecutionResultNode> rootNodes = stringExecutionResultNodeMap.values();
            List<ExecutionResultNodeZipper> unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNodes);
            System.out.println("unresolved: " + unresolvedNodes);
//            unresolvedNodes.stream().map(notResolvedObjectResultNode -> {
//                Mono<Map<String, ExecutionResultNode>> subSelection = executeSubSelection(notResolvedObjectResultNode.getFetchedValueAnalysis().getFieldSubSelection());
//            });
            return null;
        }).subscribe();
        return nextLevelNodes;
    }


    private Mono<ExecutionResultNode> createResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        if (fetchedValueAnalysis.isNullValue() && fetchedValueAnalysis.getExecutionStepInfo().isNonNullType()) {
            NonNullableFieldWasNullException nonNullableFieldWasNullException =
                    new NonNullableFieldWasNullException(fetchedValueAnalysis.getExecutionStepInfo(), fetchedValueAnalysis.getExecutionStepInfo().getPath());
            return Mono.just(new ExecutionResultNode.LeafExecutionResultNode(fetchedValueAnalysis, nonNullableFieldWasNullException));
        }
        if (fetchedValueAnalysis.isNullValue()) {
            return Mono.just(new ExecutionResultNode.LeafExecutionResultNode(fetchedValueAnalysis, null));
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.OBJECT) {
            return createObjectResultNode(fetchedValueAnalysis);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
            return createListResultNode(fetchedValueAnalysis);
        }
        return Mono.just(new ExecutionResultNode.LeafExecutionResultNode(fetchedValueAnalysis, null));
    }

    private Mono<ExecutionResultNode> createObjectResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        return Mono.just(new ExecutionResultNode.NotResolvedObjectResultNode(fetchedValueAnalysis));
    }

    private Optional<NonNullableFieldWasNullException> getFirstNonNullableException(Collection<ExecutionResultNode> collection) {
        return collection.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    private Mono<ExecutionResultNode> createListResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        List<Mono<ExecutionResultNode>> listElements = fetchedValueAnalysis
                .getChildren()
                .stream()
                .map(this::createResultNode)
                .collect(Collectors.toList());
        boolean listIsNonNull = fetchedValueAnalysis.getExecutionStepInfo().isNonNullType();
        return Flux.mergeSequential(listElements)
                .collectList()
                .map(executionResultNodes -> {
                    Optional<NonNullableFieldWasNullException> subException = getFirstNonNullableException(executionResultNodes);
                    if (listIsNonNull && subException.isPresent()) {
                        NonNullableFieldWasNullException listException = new NonNullableFieldWasNullException(subException.get());
                        return new ExecutionResultNode.ListExecutionResultNode(fetchedValueAnalysis, listException, executionResultNodes);
                    }
                    return new ExecutionResultNode.ListExecutionResultNode(fetchedValueAnalysis, null, executionResultNodes);
                });
    }


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
                .collect(Collectors.toList());

        return Flux.merge(fetchedValues);
    }


    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }


}
