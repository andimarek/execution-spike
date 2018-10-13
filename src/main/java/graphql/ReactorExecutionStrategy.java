package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.language.Field;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ReactorExecutionStrategy {

    ExecutionInfoFactory executionInfoFactory;
    FetchValue fetchValue;

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;

    public ReactorExecutionStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.fetchValue = new FetchValue(executionContext);
        this.executionInfoFactory = new ExecutionInfoFactory(executionContext);
    }

    public Mono<Map<String, Object>> execute(FieldSubSelection fieldSubSelection) {
        return fetchAndAnalyze(fieldSubSelection)
                .flatMap(fetchedValueAnalysis -> Mono.zip(Mono.just(fetchedValueAnalysis), convertFetchedValue(fetchedValueAnalysis)))
                .reduce(new ConcurrentHashMap<>(), (acc, tuple) -> {
                    FetchedValueAnalysis fetchedValueAnalysis = tuple.getT1();
                    Object value = tuple.getT2();
                    acc.put(fetchedValueAnalysis.getName(), value);
                    return acc;
                });
    }

    private Mono<Object> convertFetchedValue(FetchedValueAnalysis fetchedValueAnalysis) {
        if (fetchedValueAnalysis.isNullValue()) {
            return Mono.empty();
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
            Mono<List<Object>> result = Flux.merge(listElements).collectList();
            return result.map(Object.class::cast);
        }
        return Mono.just(fetchedValueAnalysis.getCompletedValue());
    }


    private Flux<FetchedValueAnalysis> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionInfo newExecutionInfo = executionInfoFactory.newExecutionInfoForSubField(sameFields, fieldSubSelection.getExecutionInfo());
                    return fetchValue
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionInfo)
                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionInfo));
                })
                .collect(Collectors.toList());

        return Flux.merge(fetchedValues);
    }


    private FetchedValueAnalysis analyseValue(Object fetchedValue, String name, List<Field> field, ExecutionInfo executionInfo) {
        return fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue, name, field, executionInfo);
    }


}
