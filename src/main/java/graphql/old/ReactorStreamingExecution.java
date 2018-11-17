package graphql.old;

import graphql.Common;
import graphql.ExecutionInput;
import graphql.FieldSubSelection;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.ValuesResolver;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.parser.Parser;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;

public class ReactorStreamingExecution {

    private final FieldCollector fieldCollector = new FieldCollector();

    public Flux<ResultLeaf> execute(String query,
                                    GraphQLSchema graphQLSchema) {
        return execute(new Parser().parseDocument(query), graphQLSchema, ExecutionId.generate(), ExecutionInput.newExecutionInput().build());
    }

    public Flux<ResultLeaf> execute(Document document,
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
        coercedVariables = valuesResolver.coerceArgumentValues(graphQLSchema, variableDefinitions, inputVariables);

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


    private Flux<ResultLeaf> executeOperation(ExecutionContext executionContext, Object root, OperationDefinition operationDefinition) {

        GraphQLObjectType operationRootType;

        operationRootType = Common.getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        Map<String, List<Field>> fields = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());
        ExecutionStepInfo executionInfo = newExecutionStepInfo().type(operationRootType).path(ExecutionPath.rootPath()).build();

        FieldSubSelection fieldSubSelection = new FieldSubSelection();
        fieldSubSelection.setSource(root);
        fieldSubSelection.setFields(fields);
        fieldSubSelection.setExecutionStepInfo(executionInfo);

        ReactorStreamingExecutionStrategy reactorExecutionStrategy = new ReactorStreamingExecutionStrategy(executionContext);
        return reactorExecutionStrategy.execute(fieldSubSelection);
    }


}
