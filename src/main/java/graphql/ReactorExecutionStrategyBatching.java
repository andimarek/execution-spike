package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.language.Field;
import graphql.result.ExecutionResultNode;
import graphql.result.ExecutionResultNodeZipper;
import graphql.result.ListExecutionResultNode;
import graphql.result.MultiZipper;
import graphql.result.ObjectExecutionResultNode;
import graphql.result.ResultNodesUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.result.ObjectExecutionResultNode.RootExecutionResultNode;
import static graphql.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;
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

    public Mono<RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        Mono<RootExecutionResultNode> rootMono = fetchSubSelection(fieldSubSelection).map(RootExecutionResultNode::new);

        return rootMono.flatMap(rootNode -> {
            MultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNode);
            return nextStep(unresolvedNodes);
        }).map(finalZipper -> {
            return finalZipper.toRootNode();
        }).cast(RootExecutionResultNode.class);
    }


    private Mono<Map<String, ExecutionResultNode>> fetchSubSelection(FieldSubSelection fieldSubSelection) {
        return fetchAndAnalyze(fieldSubSelection)
                .flatMap(fetchedValueAnalysis -> Mono.zip(Mono.just(fetchedValueAnalysis), createResultNode(fetchedValueAnalysis)))
                .reduce(new LinkedHashMap<>(), (acc, tuple) -> {
                    FetchedValueAnalysis fetchedValueAnalysis = tuple.getT1();
                    ExecutionResultNode executionResultNode = tuple.getT2();
                    acc.put(fetchedValueAnalysis.getName(), executionResultNode);
                    return acc;
                });
    }

    private Mono<MultiZipper> nextStep(MultiZipper unresolvedNodes) {
        MultiZipper nextUnresolvedNodes = ResultNodesUtil.getUnresolvedNodes(unresolvedNodes.toRootNode());
        if (nextUnresolvedNodes.getZippers().size() == 0) {
            return Mono.just(nextUnresolvedNodes);
        }
        return nextStepImpl(nextUnresolvedNodes).flatMap(this::nextStep);
    }

    private Mono<MultiZipper> nextStepImpl(MultiZipper unresolvedNodes) {

        List<Mono<Tuple2<ExecutionResultNodeZipper, ExecutionResultNodeZipper>>> resolvedEntry = unresolvedNodes.getZippers().stream().map(unresolvedNodeZipper -> {
            FetchedValueAnalysis unresolvedSubSelection = unresolvedNodeZipper.getCurNode().getFetchedValueAnalysis();
            Mono<Map<String, ExecutionResultNode>> subSelection =
                    fetchSubSelection(unresolvedSubSelection.getFieldSubSelection());

            Mono<Tuple2<ExecutionResultNodeZipper, ExecutionResultNodeZipper>> oneResolvedNode = subSelection.map(newChildren -> {
                UnresolvedObjectResultNode unresolvedNode = (UnresolvedObjectResultNode)
                        unresolvedNodeZipper.getCurNode();
                ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
                ExecutionResultNodeZipper newZipper = unresolvedNodeZipper.withNode(newNode);
                return Tuples.of(unresolvedNodeZipper, newZipper);
            });
            return oneResolvedNode;
        }).collect(Collectors.toList());

        return Flux.merge(resolvedEntry).collectList().map(tuple2s -> {
            MultiZipper newMultiZipper = unresolvedNodes;
            for (Tuple2<ExecutionResultNodeZipper, ExecutionResultNodeZipper> tuple : tuple2s) {
                newMultiZipper = newMultiZipper.withReplacedZipper(tuple.getT1(), tuple.getT2());
            }
            return newMultiZipper;
        });
    }

//    private Mono<? extends Map<String, ExecutionResultNode>> resolvedEntriesToMap(List<Mono<Tuple2<String, ExecutionResultNode>>> resolvedEntries) {
//        Mono<List<Tuple2<String, ExecutionResultNode>>> monoOfEntries = Flux.merge(resolvedEntries).collectList();
//        return monoOfEntries.map(entries -> {
//            Map<String, ExecutionResultNode> result = new LinkedHashMap<>();
//            entries.forEach(entry -> result.put(entry.getT1(), entry.getT2()));
//            return result;
//        });
//    }

//    private Mono<Tuple2<String, ExecutionResultNode>> resolveNode(Tuple2<String, ExecutionResultNode> keyAndNode) {
//        // done recursively unresolved for each unresolved node
//        MultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(keyAndNode.getT2());
//        if (unresolvedNodes.getZippers().size() == 0) {
//            return Mono.just(keyAndNode);
//        }
//        // the node can contain n unresolved sub nodes
//        List<Mono<Tuple2<ExecutionResultNodeZipper, ExecutionResultNodeZipper>>> resolvedEntry = unresolvedNodes.getZippers().stream().map(unresolvedNodeZipper -> {
//
//            FetchedValueAnalysis unresolvedSubSelection = unresolvedNodeZipper.getCurNode().getFetchedValueAnalysis();
//            Mono<Map<String, ExecutionResultNode>> subSelection =
//                    fetchSubSelection(unresolvedSubSelection.getFieldSubSelection());
//
//            Mono<Tuple2<ExecutionResultNodeZipper, ExecutionResultNodeZipper>> oneResolvedNode = subSelection.map(newChildren -> {
//                UnresolvedObjectResultNode unresolvedNode = (UnresolvedObjectResultNode)
//                        unresolvedNodeZipper.getCurNode();
//                ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
//                ExecutionResultNodeZipper newZipper = unresolvedNodeZipper.withNode(newNode);
//                return Tuples.of(unresolvedNodeZipper, newZipper);
//            });
//            return oneResolvedNode;
//        }).collect(Collectors.toList());
//
//        return Flux.merge(resolvedEntry).collectList().map(tuple2s -> {
//            MultiZipper newMultiZipper = unresolvedNodes;
//            for (Tuple2<ExecutionResultNodeZipper, ExecutionResultNodeZipper> tuple : tuple2s) {
//                newMultiZipper = newMultiZipper.withReplacedZipper(tuple.getT1(), tuple.getT2());
//            }
//            return Tuples.of(keyAndNode.getT1(), newMultiZipper.toRootNode());
//        });
//    }


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
        return Mono.just(new UnresolvedObjectResultNode(fetchedValueAnalysis));
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
                        return new ListExecutionResultNode(fetchedValueAnalysis, listException, executionResultNodes);
                    }
                    return new ListExecutionResultNode(fetchedValueAnalysis, null, executionResultNodes);
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
