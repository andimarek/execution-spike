//package graphql;
//
//import graphql.execution.ExecutionContext;
//import graphql.execution.ExecutionStepInfo;
//import graphql.execution.NonNullableFieldWasNullException;
//import graphql.language.Field;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.stream.Collectors;
//
//import static graphql.FetchedValueAnalysis.FetchedValueType.LIST;
//import static graphql.FetchedValueAnalysis.FetchedValueType.OBJECT;
//import static graphql.FetchedValueAnalysis.newFetchedValueAnalysis;
//
//public class DefaultExecutionStrategy {
//
//
//    ExecutionStepInfoFactory executionInfoFactory;
//    ValueFetcher valueFetcher;
//
//    private final ExecutionContext executionContext;
//    private FetchedValueAnalyzer fetchedValueAnalyzer;
//
//
//    public DefaultExecutionStrategy(ExecutionContext executionContext) {
//        this.executionContext = executionContext;
//        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
//        this.valueFetcher = new ValueFetcher(executionContext);
//        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
//    }
//
//    public Mono<Map<String, Object>> execute(FieldSubSelection fieldSubSelection) {
////        return fetchAndAnalyze(fieldSubSelection)
////                .reduce(, (acc, tuple) -> {
////                    FetchedValueAnalysis fetchedValueAnalysis = tuple.getT1();
////                    Object value = tuple.getT2();
////                    if (value == NULL_VALUE) {
////                        value = null;
////                    }
////                    acc.put(fetchedValueAnalysis.getName(), value);
////                    return acc;
////                });
//
//        FetchedValueAnalysis rootValue = newFetchedValueAnalysis(OBJECT)
//                .name("ROOT")
//                .executionStepInfo(fieldSubSelection.getExecutionStepInfo())
//                .fieldSubSelection(fieldSubSelection)
//                .build();
//
//        ExecutionResultNode root = new ExecutionResultNode(rootValue, null);
//        ExecutionResultNodeZipper rootZipper = new ExecutionResultNodeZipper(root, Collections.emptyList());
//        return executeImpl(Arrays.asList(rootZipper)).map(zippers -> {
//            return Collections.emptyMap();
//        });
//    }
//
//    private Mono<List<ExecutionResultNodeZipper>> executeImpl(List<ExecutionResultNodeZipper> curZippers) {
//        List<ExecutionResultNodeZipper> leafs = filterOutLeafs(curZippers);
//        if (leafs.isEmpty()) {
//            return Mono.empty();
//        }
//        List<Mono<List<ExecutionResultNodeZipper>>> nextZipperGroups = nextZipperGroups(leafs).stream()
//                .map(this::fetchAndAnalyze).collect(Collectors.toList());
//
//        //TODO: expand Lists into Nodes
//
//        Mono<List<List<ExecutionResultNodeZipper>>> listMono = Flux.merge(nextZipperGroups).collectList();
//        return listMono.flatMap(lists -> {
//            List<ExecutionResultNodeZipper> allLeafs = lists.stream().flatMap(List::stream).collect(Collectors.toList());
//            return executeImpl(allLeafs);
//        });
//    }
//
//    private List<ExecutionResultNode> expandLists(ExecutionResultNode node) {
//        AtomicInteger index = new AtomicInteger();
//        return node.getFetchedValueAnalysis().getChildren().stream().map(fetchedValueAnalysis -> {
//
//        }).collect(Collectors.toList());
//    }
//
//    private List<ExecutionResultNodeZipper> expandList(ExecutionResultNodeZipper listNode) {
//        AtomicInteger index = new AtomicInteger();
//        return listNode.getCurNode().getFetchedValueAnalysis().getChildren().stream().map(fetchedValueAnalysis -> {
//            ExecutionResultNode executionResultNode = new ExecutionResultNode(fetchedValueAnalysis, null);
//            return ExecutionResultNodeZipper.newZipper(executionResultNode, listNode, index.getAndIncrement());
//        }).collect(Collectors.toList());
//    }
//
//    private List<ExecutionResultNodeZipper> filterOutLeafs(List<ExecutionResultNodeZipper> zippers) {
////        return zippers.stream()
////                .filter(executionResultNodeZipper -> executionResultNodeZipper.getCurNode().getFetchedValueAnalysis().getValueType() == OBJECT)
////                .collect(Collectors.toList());
//        return zippers;
//    }
//
//    private Collection<List<ExecutionResultNodeZipper>> nextZipperGroups(List<ExecutionResultNodeZipper> allZips) {
//
//        Map<Field, List<ExecutionResultNodeZipper>> zipperByField = new LinkedHashMap<>();
//        for (int i = 0; i < allZips.size(); i++) {
//            ExecutionResultNodeZipper zipper = allZips.get(i);
//            Field field = zipper.getCurNode().getFetchedValueAnalysis().getField();
//            zipperByField.computeIfAbsent(field, __ -> new ArrayList<>());
//            zipperByField.get(field).add(zipper);
//        }
//        return zipperByField.values();
//    }
//
//    private ExecutionResultNode convertFetchedValue(FetchedValueAnalysis fetchedValueAnalysis) {
//
//        if (fetchedValueAnalysis.isNullValue() && fetchedValueAnalysis.getExecutionStepInfo().isNonNullType()) {
//            NonNullableFieldWasNullException exception = new NonNullableFieldWasNullException(fetchedValueAnalysis.getExecutionStepInfo(), fetchedValueAnalysis.getExecutionStepInfo().getPath());
//            ExecutionResultNode executionResultNode = new ExecutionResultNode(fetchedValueAnalysis, exception);
//            return executionResultNode;
//        }
//
//        if (fetchedValueAnalysis.getValueType() == OBJECT) {
//            return new ExecutionResultNode(fetchedValueAnalysis, null);
//        }
//        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
//            List<ExecutionResultNode> listElements = fetchedValueAnalysis
//                    .getChildren()
//                    .stream()
//                    .map(this::convertFetchedValue)
//                    .collect(Collectors.toList());
//            return convertList(fetchedValueAnalysis, listElements);
//
//        }
//        return new ExecutionResultNode(fetchedValueAnalysis, null);
//    }
//
//    private ExecutionResultNode convertList(FetchedValueAnalysis fetchedValueAnalysis, List<ExecutionResultNode> listElements) {
//        GraphQLType listElementType = ((GraphQLList) fetchedValueAnalysis.getExecutionStepInfo().getUnwrappedNonNullType()).getWrappedType();
//        return new ExecutionResultNode(fetchedValueAnalysis, null, listElements);
//    }
//
////    private Flux<FetchedValueAnalysis> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
////        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
////                .map(entry -> {
////                    List<Field> sameFields = entry.getValue();
////                    String name = entry.getKey();
////                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fieldSubSelection.getExecutionStepInfo());
////                    return valueFetcher
////                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
////                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
////                })
////                .collect(Collectors.toList());
////
////        return Flux.merge(fetchedValues);
////    }
//
//    private Mono<List<ExecutionResultNode>> fetchAndAnalyze(ExecutionResultNode root, List<ExecutionResultNode> leafGroup) {
//        // every element in ExecutionResultNode must be of type OBJECT and have the same subSelection
//        FieldSubSelection fieldSubSelection = getFieldSubSelection(leafGroup);
//
//        // first list: list of subFields.
//        // Mono<List<>>: size == leafGroup.size()
//        List<Mono<List<FetchedValueAnalysis>>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
//                .map(entry -> {
//                    List<Field> sameFields = entry.getValue();
//                    String name = entry.getKey();
//                    List<ExecutionStepInfo> stepInfosForSubField = leafGroup.stream().map(executionResultNode -> {
//                        FieldSubSelection fss = executionResultNode.getFetchedValueAnalysis().getFieldSubSelection();
//                        return executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fss.getExecutionStepInfo());
//                    }).collect(Collectors.toList());
//                    List<Object> sources = leafGroup.stream().map(executionResultNode -> executionResultNode.getFetchedValueAnalysis().getFieldSubSelection().getSource()).collect(Collectors.toList());
//                    return valueFetcher
//                            .fetchBatchedValues(sources, sameFields, stepInfosForSubField)
//                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, stepInfosForSubField));
//                })
//                .collect(Collectors.toList());
//
//        return Flux.merge(fetchedValues).collectList().onErrorMap(throwable -> {
//            throwable.printStackTrace();
//            return throwable;
//        }).map(fetchedValuesMatrix -> {
//
//            List<ExecutionResultNodeZipper> result = new ArrayList<>();
//            List<List<FetchedValueAnalysis>> newChildsPerNode = Common.transposeMatrix(fetchedValuesMatrix);
//
//            for (int i = 0; i < newChildsPerNode.size(); i++) {
//                ExecutionResultNode oldLeaf = leafGroup.get(i);
//                List<FetchedValueAnalysis> fetchedValuesForNode = newChildsPerNode.get(i);
//                int index = 0;
//                List<ExecutionResultNodeZipper> newChildZippers = fetchedValuesForNode.stream()
//                        .map(fetchedValueAnalysis -> {
//                            ExecutionResultNode node = new ExecutionResultNode(fetchedValueAnalysis, null);
//                            return newZipper;
//                        })
//                        .collect(Collectors.toList());
//                result.addAll(newChildZippers);
//            }
//            return result;
//        });
//    }
//
//    private FieldSubSelection getFieldSubSelection(List<ExecutionResultNode> leafGroup) {
//        return leafGroup.get(0).getFetchedValueAnalysis().getFieldSubSelection();
//    }
//
//    private List<ExecutionStepInfo> getExecutionStepInfos(List<ExecutionResultNode> nodes) {
//        return nodes.stream().map(executionResultNode -> executionResultNode.getFetchedValueAnalysis().getExecutionStepInfo()).collect(Collectors.toList());
//    }
//
//
//    private List<FetchedValueAnalysis> analyseValue(List<FetchedValue> fetchedValues,
//                                                    String name,
//                                                    List<Field> field,
//                                                    List<ExecutionStepInfo> stepInfos) {
//        List<FetchedValueAnalysis> result = new ArrayList<>();
//        for (int i = 0; i < fetchedValues.size(); i++) {
//            FetchedValue fetchedValue = fetchedValues.get(i);
//            FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, stepInfos.get(i));
//            //TODO: should be set into the analyzer
//            fetchedValueAnalysis.setFetchedValue(fetchedValue);
//            result.add(fetchedValueAnalysis);
//        }
//        return result;
//    }
//
//
//}
