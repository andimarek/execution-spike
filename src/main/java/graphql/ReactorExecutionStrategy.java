package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.language.Field;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.ValueFetcher.NULL_VALUE;

public class ReactorExecutionStrategy {

    ExecutionInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public ReactorExecutionStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionInfoFactory(executionContext);
    }

    public Mono<Map<String, Object>> execute(FieldSubSelection fieldSubSelection) {
        return fetchAndAnalyze(fieldSubSelection)
                .flatMap(fetchedValueAnalysis -> Mono.zip(Mono.just(fetchedValueAnalysis), convertFetchedValue(fetchedValueAnalysis)))
                // here it is LinkedHashMap to allow for null values, this means reduce must be non concurrent
                .reduce(new LinkedHashMap<>(), (acc, tuple) -> {
                    FetchedValueAnalysis fetchedValueAnalysis = tuple.getT1();
                    Object value = tuple.getT2();
                    if (value == NULL_VALUE) {
                        value = null;
                    }
                    acc.put(fetchedValueAnalysis.getName(), value);
                    return acc;
                });
    }

    private Mono<Object> convertFetchedValue(FetchedValueAnalysis fetchedValueAnalysis) {
        if (fetchedValueAnalysis.isNullValue() && fetchedValueAnalysis.getExecutionInfo().isNonNullType()) {
            return Mono.error(new NonNullableFieldWasNullException(fetchedValueAnalysis.getExecutionInfo(), fetchedValueAnalysis.getExecutionInfo().getPath()));
        }
        if (fetchedValueAnalysis.isNullValue()) {
            return Mono.just(NULL_VALUE);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.OBJECT) {
            FieldSubSelection nextLevelSubSelection = fetchedValueAnalysis.getFieldSubSelection();
            return execute(nextLevelSubSelection).map(Object.class::cast);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
            List<Mono<Object>> listElements = fetchedValueAnalysis
                    .getChildren()
                    .stream()
                    .map(this::convertFetchedValue)
                    .collect(Collectors.toList());
            return convertList(fetchedValueAnalysis, listElements);
        }
        return Mono.just(fetchedValueAnalysis.getCompletedValue());
    }

    private Mono<Object> convertList(FetchedValueAnalysis fetchedValueAnalysis, List<Mono<Object>> listElements) {
        GraphQLType listElementType = fetchedValueAnalysis.getExecutionInfo().castType(GraphQLList.class).getWrappedType();
        boolean listElementNonNull = listElementType instanceof GraphQLNonNull;

        List<Mono<Object>> monosWithErrorHandler = listElements.stream().map(elementMono -> elementMono
                .onErrorResume(NonNullableFieldWasNullException.class, e ->
                        handleNonNullableExceptionForListElement(listElementNonNull, e, fetchedValueAnalysis))).
                collect(Collectors.toList());

        return Flux.mergeSequential(monosWithErrorHandler)
                .collectList()
                .cast(Object.class)
                .onErrorResume(NonNullableFieldWasNullException.class, e -> handNonNullableException(fetchedValueAnalysis, e))
                .map(this::replaceNullValuesWithAcutalNull);
    }

    private Mono<?> handleNonNullableExceptionForListElement(boolean listElementNonNull, NonNullableFieldWasNullException e, FetchedValueAnalysis parentAnalysis) {
        if (listElementNonNull) {
            return Mono.error(new NonNullableFieldWasNullException(e));
        } else {
            return Mono.just(NULL_VALUE);
        }
    }

    private Mono<?> handNonNullableException(FetchedValueAnalysis fetchedValueAnalysis, NonNullableFieldWasNullException e) {
        if (fetchedValueAnalysis.getExecutionInfo().isNonNullType()) {
            return Mono.error(new NonNullableFieldWasNullException(e));
        } else {
            return Mono.just(NULL_VALUE);
        }
    }

    private Object replaceNullValuesWithAcutalNull(Object listOrNullValue) {
        if (listOrNullValue instanceof List) {
            return ((List) listOrNullValue).stream().map(o -> o == NULL_VALUE ? null : o).collect(Collectors.toList());
        } else {
            return listOrNullValue;
        }
    }


    private Flux<FetchedValueAnalysis> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionInfo newExecutionInfo = executionInfoFactory.newExecutionInfoForSubField(sameFields, fieldSubSelection.getExecutionInfo());
                    return valueFetcher
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionInfo)
                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionInfo));
                })
                .collect(Collectors.toList());

        return Flux.merge(fetchedValues);
    }


    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }


}
