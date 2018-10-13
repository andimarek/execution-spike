package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.UnresolvedTypeException;
import graphql.language.Field;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.util.FpKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static graphql.FetchedValueAnalysis.FetchedValueType.ENUM;
import static graphql.FetchedValueAnalysis.FetchedValueType.LIST;
import static graphql.FetchedValueAnalysis.FetchedValueType.OBJECT;
import static graphql.FetchedValueAnalysis.FetchedValueType.SCALAR;
import static graphql.FetchedValueAnalysis.newFetchedValueAnalysis;
import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.schema.GraphQLTypeUtil.isList;
import static java.util.Collections.singletonList;

public class FetchedValueAnalyzer {

    private final ExecutionContext executionContext;
    ResolveType resolveType;
    FieldCollector fieldCollector = new FieldCollector();
    ExecutionInfoFactory executionInfoFactory;


    public FetchedValueAnalyzer(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.resolveType = new ResolveType(executionContext);
        this.executionInfoFactory = new ExecutionInfoFactory(executionContext);
    }

    private static final Logger log = LoggerFactory.getLogger(FetchedValueAnalyzer.class);


    /**
     * scalar: the value, null and/or error
     * enum: same as scalar
     * list: list of X: X can be list again, list of scalars or enum or objects
     */
    public FetchedValueAnalysis analyzeFetchedValue(Object value, String name, List<Field> field, ExecutionInfo executionInfo) throws NonNullableFieldWasNullException {
        Object unboxedValue = UnboxPossibleOptional.unboxPossibleOptional(value);
        GraphQLType fieldType = executionInfo.getType();

        FetchedValueAnalysis result = null;
        if (isList(fieldType)) {
            result = analyzeList(unboxedValue, name, executionInfo);
        } else if (fieldType instanceof GraphQLScalarType) {
            result = analyzeScalarValue(unboxedValue, name, (GraphQLScalarType) fieldType, executionInfo);
        } else if (fieldType instanceof GraphQLEnumType) {
            result = analyzeEnumValue(unboxedValue, name, (GraphQLEnumType) fieldType, executionInfo);
        }
        if (result != null) {
            result.setExecutionInfo(executionInfo);
            return result;
        }

        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper
        //
        GraphQLObjectType resolvedObjectType;
        try {
            resolvedObjectType = resolveType.resolveType(field.get(0), unboxedValue, executionInfo.getArguments(), fieldType);
            return analyzeObject(unboxedValue, name, resolvedObjectType, executionInfo);
        } catch (UnresolvedTypeException ex) {
            return handleUnresolvedTypeProblem(name, executionInfo, ex);
        }
    }


    private FetchedValueAnalysis handleUnresolvedTypeProblem(String name, ExecutionInfo executionInfo, UnresolvedTypeException e) {
        UnresolvedTypeError error = new UnresolvedTypeError(executionInfo.getPath(), executionInfo, e);
        return newFetchedValueAnalysis(OBJECT)
                .name(name)
                .nullValue()
                .error(error)
                .build();
    }

    private FetchedValueAnalysis analyzeList(Object fetchedValue, String name, ExecutionInfo executionInfo) {
        if (fetchedValue == null) {
            return newFetchedValueAnalysis(LIST)
                    .name(name)
                    .nullValue()
                    .build();
        }

        if (fetchedValue.getClass().isArray() || fetchedValue instanceof Iterable) {
            Collection<Object> collection = FpKit.toCollection(fetchedValue);
            return analyzeIterable(collection, name, executionInfo);
        } else {
            TypeMismatchError error = new TypeMismatchError(executionInfo.getPath(), executionInfo.getType());
            return newFetchedValueAnalysis(LIST)
                    .name(name)
                    .nullValue()
                    .error(error)
                    .build();
        }

    }


    private FetchedValueAnalysis analyzeIterable(Iterable<Object> iterableValues, String name, ExecutionInfo executionInfo) {

        Collection<Object> values = FpKit.toCollection(iterableValues);
        List<FetchedValueAnalysis> children = new ArrayList<>();
        int index = 0;
        for (Object item : values) {

            ExecutionInfo executionInfoForListElement = executionInfoFactory.newExecutionInfoForListElement(executionInfo, index);

            children.add(analyzeFetchedValue(item, name, Arrays.asList(executionInfo.getField()), executionInfoForListElement));
            index++;
        }
        return newFetchedValueAnalysis(LIST)
                .name(name)
                .children(children)
                .build();

    }


    private FetchedValueAnalysis analyzeScalarValue(Object fetchedValue, String name, GraphQLScalarType scalarType, ExecutionInfo executionInfo) {
        if (fetchedValue == null) {
            return newFetchedValueAnalysis(SCALAR)
                    .name(name)
                    .nullValue()
                    .build();
        }
        Object serialized;
        try {
            serialized = scalarType.getCoercing().serialize(fetchedValue);
        } catch (CoercingSerializeException e) {
            SerializationError error = new SerializationError(executionInfo.getPath(), e);
            return newFetchedValueAnalysis(SCALAR)
                    .error(error)
                    .name(name)
                    .nullValue()
                    .build();
        }

        // TODO: fix that: this should not be handled here
        //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
        if (serialized instanceof Double && ((Double) serialized).isNaN()) {
            return newFetchedValueAnalysis(SCALAR)
                    .name(name)
                    .nullValue()
                    .build();
        }
        // handle non null

        return newFetchedValueAnalysis(SCALAR)
                .completedValue(serialized)
                .name(name)
                .build();
    }

    private FetchedValueAnalysis analyzeEnumValue(Object fetchedValue, String name, GraphQLEnumType enumType, ExecutionInfo executionInfo) {
        if (fetchedValue == null) {
            return newFetchedValueAnalysis(SCALAR)
                    .nullValue()
                    .name(name)
                    .build();

        }
        Object serialized;
        try {
            serialized = enumType.getCoercing().serialize(fetchedValue);
        } catch (CoercingSerializeException e) {
            SerializationError error = new SerializationError(executionInfo.getPath(), e);
            return newFetchedValueAnalysis(SCALAR)
                    .nullValue()
                    .error(error)
                    .name(name)
                    .build();
        }
        // handle non null values
        return newFetchedValueAnalysis(ENUM)
                .name(name)
                .completedValue(serialized)
                .build();
    }

    private FetchedValueAnalysis analyzeObject(Object fetchedValue, String name, GraphQLObjectType resolvedObjectType, ExecutionInfo executionInfo) {

        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedObjectType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        Map<String, List<Field>> subFields = fieldCollector.collectFields(collectorParameters, singletonList(executionInfo.getField()));

        // it is not really a new step but rather a refinement
        ExecutionInfo newExecutionInfoWithResolvedType = executionInfo.treatAs(resolvedObjectType);

        FieldSubSelection fieldSubSelection = new FieldSubSelection();
        fieldSubSelection.setSource(fetchedValue);
        fieldSubSelection.setExecutionInfo(newExecutionInfoWithResolvedType);
        fieldSubSelection.setFields(subFields);


        FetchedValueAnalysis result = newFetchedValueAnalysis(OBJECT)
                .name(name)
                .fieldSubSelection(fieldSubSelection)
                .fetchedValue(fetchedValue)
                .build();
        result.setExecutionInfo(newExecutionInfoWithResolvedType);
        return result;
    }
}
