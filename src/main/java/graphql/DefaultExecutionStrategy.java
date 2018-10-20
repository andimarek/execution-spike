package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.FetchedValueAnalysis.FetchedValueType.OBJECT;
import static graphql.FetchedValueAnalysis.newFetchedValueAnalysis;

public class DefaultExecutionStrategy {


    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public DefaultExecutionStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    public Mono<Map<String, Object>> execute(FieldSubSelection fieldSubSelection) {
//        return fetchAndAnalyze(fieldSubSelection)
//                .reduce(, (acc, tuple) -> {
//                    FetchedValueAnalysis fetchedValueAnalysis = tuple.getT1();
//                    Object value = tuple.getT2();
//                    if (value == NULL_VALUE) {
//                        value = null;
//                    }
//                    acc.put(fetchedValueAnalysis.getName(), value);
//                    return acc;
//                });

        FetchedValueAnalysis rootValue = newFetchedValueAnalysis(OBJECT)
                .name("ROOT")
                .executionStepInfo(fieldSubSelection.getExecutionStepInfo())
                .fieldSubSelection(fieldSubSelection)
                .build();

        ExecutionResultNode root = new ExecutionResultNode(rootValue, null);
        Mono<List<ExecutionResultNode>> nextLevelRoots = fetchAndAnalyze(Arrays.asList(root));
        return nextLevelRoots.map(executionResultNodes -> Collections.emptyMap());
    }


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

//    private ExecutionResultNode convertList(FetchedValueAnalysis fetchedValueAnalysis, List<ExecutionResultNode> listElements) {
////        GraphQLType listElementType = ((GraphQLList) fetchedValueAnalysis.getExecutionStepInfo().getUnwrappedNonNullType()).getWrappedType();
//        return new ExecutionResultNode(fetchedValueAnalysis, null, listElements);
//    }

//    private Flux<FetchedValueAnalysis> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
//        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
//                .map(entry -> {
//                    List<Field> sameFields = entry.getValue();
//                    String name = entry.getKey();
//                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fieldSubSelection.getExecutionStepInfo());
//                    return valueFetcher
//                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
//                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
//                })
//                .collect(Collectors.toList());
//
//        return Flux.merge(fetchedValues);
//    }

    private Mono<List<ExecutionResultNode>> fetchAndAnalyze(List<ExecutionResultNode> leafGroup) {
        FieldSubSelection fieldSubSelection = getFieldSubSelection(leafGroup);
        List<ExecutionStepInfo> stepInfos = getExecutionStepInfos(leafGroup);

        // first list: list of subFields.
        // Mono<List<>>: size == leafGroup.size()
        List<Mono<List<FetchedValueAnalysis>>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    List<ExecutionStepInfo> stepInfosForSubField = leafGroup.stream().map(executionResultNode -> {
                        FieldSubSelection fss = executionResultNode.getFetchedValueAnalysis().getFieldSubSelection();
                        return executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fss.getExecutionStepInfo());
                    }).collect(Collectors.toList());
                    List<Object> sources = leafGroup.stream().map(executionResultNode -> executionResultNode.getFetchedValueAnalysis().getFieldSubSelection().getSource()).collect(Collectors.toList());
                    return valueFetcher
                            .fetchBatchedValues(sources, sameFields, stepInfosForSubField)
                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, stepInfosForSubField));
                })
                .collect(Collectors.toList());

        return Flux.merge(fetchedValues).collectList().onErrorMap(throwable -> {
            throwable.printStackTrace();
            return throwable;
        }).map(fetchedValuesMatrix -> {
            List<ExecutionResultNode> result = new ArrayList<>();
            List<List<FetchedValueAnalysis>> newChildsPerNode = Common.transposeMatrix(fetchedValuesMatrix);
            for (int i = 0; i < newChildsPerNode.size(); i++) {
                ExecutionResultNode executionResultNode = leafGroup.get(i);
                List<FetchedValueAnalysis> fetchedValuesForNode = newChildsPerNode.get(i);
                //TODO: handle non null?
                List<ExecutionResultNode> newChildNodes = fetchedValuesForNode.stream()
                        .map(fetchedValueAnalysis -> new ExecutionResultNode(fetchedValueAnalysis, null))
                        .collect(Collectors.toList());
                result.add(executionResultNode.withChildren(newChildNodes));
            }
            return result;
        });
    }

    private FieldSubSelection getFieldSubSelection(List<ExecutionResultNode> leafGroup) {
        return leafGroup.get(0).getFetchedValueAnalysis().getFieldSubSelection();
    }

    private List<ExecutionStepInfo> getExecutionStepInfos(List<ExecutionResultNode> nodes) {
        return nodes.stream().map(executionResultNode -> executionResultNode.getFetchedValueAnalysis().getExecutionStepInfo()).collect(Collectors.toList());
    }


    private List<FetchedValueAnalysis> analyseValue(List<FetchedValue> fetchedValues,
                                                    String name,
                                                    List<Field> field,
                                                    List<ExecutionStepInfo> stepInfos) {
        List<FetchedValueAnalysis> result = new ArrayList<>();
        for (int i = 0; i < fetchedValues.size(); i++) {
            FetchedValue fetchedValue = fetchedValues.get(i);
            FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue, name, field, stepInfos.get(i));
            //TODO: should be set into the analyzer
            fetchedValueAnalysis.setFetchedValue(fetchedValue);
            result.add(fetchedValueAnalysis);
        }
        return result;
    }


}
