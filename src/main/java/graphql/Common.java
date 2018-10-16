package graphql;

import graphql.execution.MissingRootTypeException;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;

public class Common {

    public static GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
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

    public static void insertValueInResult(ResultLeaf resultLeaf, Map<Object, Object> result) {
        List<Object> pathList = resultLeaf.getExecutionPath();

        Object curContainer = result;
        int existingIndex = 0;
        for (int i = 0; i < pathList.size(); i++) {
            Object indexOrName = pathList.get(i);
            Object nextContainer;
            if (indexOrName instanceof Integer) {
                List list = (List) curContainer;
                int index = (int) indexOrName;
                nextContainer = index < list.size() ? list.get(index) : null;
            } else {
                nextContainer = ((Map) curContainer).get(indexOrName);
            }
            if (nextContainer == null) {
                existingIndex = i;
                break;
            } else {
                curContainer = nextContainer;
            }
        }
        for (int i = existingIndex; i < pathList.size() - 1; i++) {
            Object indexOrName = pathList.get(i);
            Object nextIndexOrName = pathList.get(i + 1);
            Object parentContainer = createParentContainer(nextIndexOrName);
            putInContainer(curContainer, indexOrName, parentContainer);
            curContainer = parentContainer;
        }
        putInContainer(curContainer, pathList.get(pathList.size() - 1), resultLeaf.getValue());
    }

    private static void putInContainer(Object container, Object indexOrName, Object value) {
        if (indexOrName instanceof Integer) {
            List<Object> list = (List<Object>) container;
            int index = (int) indexOrName;
            if (index < list.size()) {
                list.set(index, value);
            } else {
                while (list.size() < index) {
                    list.add(null);
                }
                list.add(value);
            }
        } else {
            ((Map) container).put(indexOrName, value);
        }
    }

    private static Object createParentContainer(Object indexOrName) {
        if (indexOrName instanceof Integer) {
            return new CopyOnWriteArrayList<>();
        } else {
            return new ConcurrentHashMap<>();
        }
    }
}
