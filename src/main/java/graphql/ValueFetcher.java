package graphql;


import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionInfo;
import graphql.execution.ExecutionPath;
import graphql.execution.ValuesResolver;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;

public class ValueFetcher {

    private final ExecutionContext executionContext;

    ValuesResolver valuesResolver = new ValuesResolver();

    private static final Logger log = LoggerFactory.getLogger(ValueFetcher.class);

    public ValueFetcher(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }


    public Mono<FetchedValue> fetchValue(Object source, List<Field> sameFields, ExecutionInfo executionInfo) {
        Field field = sameFields.get(0);
        GraphQLFieldDefinition fieldDef = executionInfo.getFieldDefinition();

        GraphqlFieldVisibility fieldVisibility = executionContext.getGraphQLSchema().getFieldVisibility();
        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldVisibility, fieldDef.getArguments(), field.getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, sameFields);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(source)
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(sameFields)
                .fieldType(fieldType)
                .executionInfo(executionInfo)
                .parentType(executionInfo.getParent().getType())
                .selectionSet(fieldCollector)
                .build();

        ExecutionId executionId = executionContext.getExecutionId();
        ExecutionPath path = executionInfo.getPath();
        return Mono
                .create(sink -> createMonoImpl(fieldDef, environment, executionId, path, sink))
                .map(rawFetchedValue -> new FetchedValue(rawFetchedValue, rawFetchedValue, Collections.emptyList()))
                .onErrorResume(exception -> {
                    ExceptionWhileDataFetching exceptionWhileDataFetching = new ExceptionWhileDataFetching(path, exception, field.getSourceLocation());
                    FetchedValue fetchedValue = new FetchedValue(
                            null,
                            null,
                            Collections.singletonList(exceptionWhileDataFetching));
                    return Mono.just(fetchedValue);
                })
                .map(result -> unboxPossibleDataFetcherResult(sameFields, path, result))
                .map(this::unboxPossibleOptional);
    }

    private FetchedValue unboxPossibleOptional(FetchedValue result) {
        return new FetchedValue(UnboxPossibleOptional.unboxPossibleOptional(result.getFetchedValue()), result.getRawFetchedValue(), result.getErrors());

    }

    private void createMonoImpl(GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment, ExecutionId executionId, ExecutionPath path, MonoSink<Object> sink) {
        try {
            DataFetcher dataFetcher = fieldDef.getDataFetcher();
            log.debug("'{}' fetching field '{}' using data fetcher '{}'...", executionId, path, dataFetcher.getClass().getName());
            Object fetchedValueRaw = dataFetcher.get(environment);
            log.debug("'{}' field '{}' fetch returned '{}'", executionId, path, fetchedValueRaw == null ? "null" : fetchedValueRaw.getClass().getName());
            handleFetchedValue(fetchedValueRaw, sink);
        } catch (Exception e) {
            log.debug(String.format("'%s', field '%s' fetch threw exception", executionId, path), e);
            sink.error(e);
        }
    }

    private void handleFetchedValue(Object fetchedValue, MonoSink<Object> sink) {
        if (fetchedValue == null) {
            sink.success();
            return;
        }
        if (fetchedValue instanceof CompletionStage) {
            ((CompletionStage<Object>) fetchedValue).whenComplete((value, throwable) -> {
                if (throwable != null) {
                    sink.error(throwable);
                } else {
                    sink.success(value);
                }
            });
            return;
        }

        if (fetchedValue instanceof Mono) {
            ((Mono<Object>) fetchedValue).subscribe(sink::success, sink::error);
            return;
        }
        sink.success(fetchedValue);

    }

    private FetchedValue unboxPossibleDataFetcherResult(List<Field> sameField, ExecutionPath executionPath, FetchedValue result) {
        if (result.getFetchedValue() instanceof DataFetcherResult) {
            DataFetcherResult<?> dataFetcherResult = (DataFetcherResult) result.getFetchedValue();
            List<AbsoluteGraphQLError> addErrors = dataFetcherResult.getErrors().stream()
                    .map(relError -> new AbsoluteGraphQLError(sameField, executionPath, relError))
                    .collect(Collectors.toList());
            List<GraphQLError> newErrors = new ArrayList<>(result.getErrors());
            newErrors.addAll(addErrors);
            return new FetchedValue(dataFetcherResult.getData(), result.getRawFetchedValue(), newErrors);
        } else {
            return result;
        }
    }



}
