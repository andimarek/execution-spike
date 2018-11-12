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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

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

        return nextLevelNodes.flatMap(nodeByKey -> {
            List<Mono<Map.Entry<String, ExecutionResultNode>>> resolvedEntries = nodeByKey.entrySet().stream().map(this::resolveNode).collect(toList());
            return resolvedEntriesToMap(resolvedEntries);
        });
    }

    private Mono<? extends Map<String, ExecutionResultNode>> resolvedEntriesToMap(List<Mono<Map.Entry<String, ExecutionResultNode>>> resolvedEntries) {
        Mono<List<Map.Entry<String, ExecutionResultNode>>> monoOfEntries = Flux.merge(resolvedEntries).collectList();
        return monoOfEntries.map(entries -> {
            Map<String, ExecutionResultNode> result = new LinkedHashMap<>();
            entries.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        });
    }

    private Mono<Map.Entry<String, ExecutionResultNode>> resolveNode(Map.Entry<String, ExecutionResultNode> keyAndNode) {
        ExecutionResultNodeZipper unresolvedNodeZipper = ResultNodesUtil.getFirstUnresolvedNode(keyAndNode.getValue());
        if (unresolvedNodeZipper == null) {
            return Mono.just(keyAndNode);
        }
        Mono<Map<String, ExecutionResultNode>> subSelection =
                executeSubSelection(unresolvedNodeZipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection());

        Mono<Map.Entry<String, ExecutionResultNode>> oneResolvedMono = subSelection.map(newChildren -> {
            ExecutionResultNode.UnresolvedObjectResultNode unresolvedNode = (ExecutionResultNode.UnresolvedObjectResultNode) unresolvedNodeZipper.getCurNode();
            ExecutionResultNode.ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
            ExecutionResultNodeZipper resolvedNodeZipper = unresolvedNodeZipper.withNode(newNode);
            ExecutionResultNode resolvedNode = resolvedNodeZipper.toRootNode();
            return new AbstractMap.SimpleEntry<>(keyAndNode.getKey(), resolvedNode);
        });
        return oneResolvedMono.flatMap(oneResolved -> resolveNode(oneResolved));
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
        return Mono.just(new ExecutionResultNode.UnresolvedObjectResultNode(fetchedValueAnalysis));
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
                .collect(toList());
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
                .collect(toList());

        return Flux.merge(fetchedValues);
    }


    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }


}
