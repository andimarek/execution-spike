package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionInfo;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MissingRootTypeException;
import graphql.execution.NonNullableFieldValidator;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.ValuesResolver;
import graphql.execution.defer.DeferSupport;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionInfo.newExecutionInfo;
import static graphql.execution.ExecutionStrategyParameters.newParameters;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class ReactorExecution {

    private final FieldCollector fieldCollector = new FieldCollector();

    public Mono<ExecutionResult> execute(Document document,
                                         GraphQLSchema graphQLSchema,
                                         ExecutionId executionId,
                                         ExecutionInput executionInput) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, executionInput.getOperationName());
        Map<String, FragmentDefinition> fragmentsByName = getOperationResult.fragmentsByName;
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;

        ValuesResolver valuesResolver = new ValuesResolver();
        Map<String, Object> inputVariables = executionInput.getVariables();
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();

        Map<String, Object> coercedVariables;
        try {
            coercedVariables = valuesResolver.coerceArgumentValues(graphQLSchema, variableDefinitions, inputVariables);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return Mono.just(new ExecutionResultImpl((GraphQLError) rte));
            }
            return Mono.error(rte);
        }

        ExecutionContext executionContext = newExecutionContextBuilder()
                .executionId(executionId)
                .graphQLSchema(graphQLSchema)
                .context(executionInput.getContext())
                .root(executionInput.getRoot())
                .fragmentsByName(fragmentsByName)
                .variables(coercedVariables)
                .document(document)
                .operationDefinition(operationDefinition)
                .dataLoaderRegistry(executionInput.getDataLoaderRegistry())
                .build();

        return executeOperation(executionContext, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    private Mono<ExecutionResult> executeOperation(ExecutionContext executionContext, Object root, OperationDefinition operationDefinition) {

        OperationDefinition.Operation operation = operationDefinition.getOperation();
        GraphQLObjectType operationRootType;

        operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        Map<String, List<Field>> fields = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());
        ExecutionInfo executionInfo = newExecutionInfo().type(operationRootType).path(ExecutionPath.rootPath()).build();

        FieldSubSelection fieldSubSelection = new FieldSubSelection();
        fieldSubSelection.setSource(root);
        fieldSubSelection.setFields(fields);
        fieldSubSelection.setExecutionInfo(executionInfo);

        ReactorExecutionStrategy reactorExecutionStrategy = new ReactorExecutionStrategy(executionContext);
        return reactorExecutionStrategy.execute(fieldSubSelection).map(stringObjectMap -> {
            //TODO: handle errors
            return ExecutionResultImpl.newExecutionResult()
                    .data(stringObjectMap)
                    .build();
        });
    }

    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        OperationDefinition.Operation operation = operationDefinition.getOperation();
        if (operation == MUTATION) {
            GraphQLObjectType mutationType = graphQLSchema.getMutationType();
            if (mutationType == null) {
                throw new MissingRootTypeException("Schema is not configured for mutations.", operationDefinition.getSourceLocation());
            }
            return mutationType;
        } else if (operation == QUERY) {
            GraphQLObjectType queryType = graphQLSchema.getQueryType();
            if (queryType == null) {
                throw new MissingRootTypeException("Schema does not define the required query root type.", operationDefinition.getSourceLocation());
            }
            return queryType;
        } else if (operation == SUBSCRIPTION) {
            GraphQLObjectType subscriptionType = graphQLSchema.getSubscriptionType();
            if (subscriptionType == null) {
                throw new MissingRootTypeException("Schema is not configured for subscriptions.", operationDefinition.getSourceLocation());
            }
            return subscriptionType;
        } else {
            return assertShouldNeverHappen("Unhandled case.  An extra operation enum has been added without code support");
        }
    }

}
