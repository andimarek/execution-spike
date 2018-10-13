package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.execution.ExecutionPath;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.List;
import java.util.Map;

public class ExecutionInfoFactory {

    private final ExecutionContext executionContext;

    ValuesResolver valuesResolver = new ValuesResolver();

    public ExecutionInfoFactory(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public ExecutionInfo newExecutionInfoForSubField(List<Field> sameFields, ExecutionInfo parentInfo) {
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

    public ExecutionInfo newExecutionInfoForListElement(ExecutionInfo executionInfo, int index) {
        Field field = executionInfo.getField();
        GraphQLList fieldType = executionInfo.castType(GraphQLList.class);
        GraphQLFieldDefinition fieldDef = executionInfo.getFieldDefinition();
        ExecutionPath indexedPath = executionInfo.getPath().segment(index);
        return ExecutionInfo.newExecutionInfo()
                .parentInfo(executionInfo)
                .type(fieldType.getWrappedType())
                .path(indexedPath)
                .fieldDefinition(fieldDef)
                .field(field)
                .build();
    }

    private static String mkNameForPath(List<Field> currentField) {
        Field field = currentField.get(0);
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }
}
