package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.execution.ExecutionPath;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.visibility.GraphqlFieldVisibility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReactorExecutionStrategy {

    FetchValue fetchValue;
    ValuesResolver valuesResolver = new ValuesResolver();

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;

    public ReactorExecutionStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.fetchValue = new FetchValue(executionContext);
    }

    public Mono<Map<String, Object>> execute(FieldSubSelection fieldSubSelection) {
        Mono<Map<String, Object>> result = resolveValues(fieldSubSelection).reduce(new ConcurrentHashMap<>(), (acc, fetchedValueAnalysis) -> {
            Mono<Object> convertedValue = convertFetchedValue(fetchedValueAnalysis);
            acc.put(fetchedValueAnalysis.getName(), convertedValue);
            return acc;
        });
        return result;
    }

    private Mono<Object> convertFetchedValue(FetchedValueAnalysis fetchedValueAnalysis) {
        if (fetchedValueAnalysis.isNullValue()) {
            return Mono.empty();
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.OBJECT) {
            FieldSubSelection nextLevelSubSelection = fetchedValueAnalysis.getFieldSubSelection();
            // what now? return a
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
            List<Mono<Object>> listElements = fetchedValueAnalysis.getChildren().stream().map(fetchedValueAnalysis1 -> convertFetchedValue(fetchedValueAnalysis)).collect(Collectors.toList());
//            CopyOnWriteArrayList<Object> list = new CopyOnWriteArrayList<>();
//            listElements.forEach(listElement -> {
//                listElement.doOnNext(list::add);
//            });

//            Mono<Object> reduced = fluxElements.reduce(initial, (acc, listElement) -> {
//                acc.add(listElement);
//                return acc;
//            });
        }
        return Mono.just(fetchedValueAnalysis.getCompletedValue());
    }


    private Flux<FetchedValueAnalysis> resolveValues(FieldSubSelection fieldSubSelection) {
        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionInfo newExecutionInfo = newExecutionInfoForSubField(sameFields, fieldSubSelection.getExecutionInfo());
                    return fetchValue.fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionInfo).map(fetchValue -> {
                        return analyseValue(fetchValue, name, sameFields, newExecutionInfo);
                    });
                })
                .collect(Collectors.toList());

        return Flux.merge(fetchedValues);
    }


    private FetchedValueAnalysis analyseValue(Object fetchedValue, String name, List<Field> field, ExecutionInfo executionInfo) {
        return fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue, name, field, executionInfo);
    }

    private ExecutionInfo newExecutionInfoForSubField(List<Field> sameFields, ExecutionInfo parentInfo) {
        Field field = sameFields.get(0);
        GraphQLObjectType parentType = parentInfo.castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDefinition = Introspection.getFieldDef(executionContext.getGraphQLSchema(), parentType, field.getName());
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<Argument> fieldArgs = field.getArguments();
        GraphqlFieldVisibility fieldVisibility = executionContext.getGraphQLSchema().getFieldVisibility();
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldVisibility, fieldDefinition.getArguments(), fieldArgs, executionContext.getVariables());

        ExecutionPath newPath = parentInfo.getPath().segment(mkNameForPath(sameFields));

        return ExecutionInfo.newExecutionInfo()
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .field(field)
                .path(newPath)
                .parentInfo(parentInfo)
                .arguments(argumentValues)
                .build();
    }

    private static String mkNameForPath(List<Field> currentField) {
        Field field = currentField.get(0);
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }

}
