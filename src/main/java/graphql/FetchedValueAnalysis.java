package graphql;

import graphql.execution.ExecutionInfo;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;

public class FetchedValueAnalysis {


    public enum FetchedValueType {
        OBJECT,
        LIST,
        SCALAR,
        ENUM
    }


    private FetchedValueType valueType;
    private List<GraphQLError> errors;

    private Object completedValue;

    // only available for LIST
    private List<FetchedValueAnalysis> children;

    private boolean nullValue;

    private Field field;
    private String name;

    // only for object
    private FieldSubSelection fieldSubSelection;

    private ExecutionInfo executionInfo;
    private FetchedValue fetchedValue;


    private FetchedValueAnalysis(Builder builder) {
        setValueType(builder.valueType);
        setErrors(builder.errors);
        setCompletedValue(builder.completedValue);
        setFetchedValue(builder.fetchedValue);
        setChildren(builder.children);
        setNullValue(builder.nullValue);
        setField(builder.field);
        setName(builder.name);
        setFieldSubSelection(builder.fieldSubSelection);
        setExecutionInfo(builder.executionInfo);
    }


    public static Builder newBuilder(FetchedValueAnalysis copy) {
        Builder builder = new Builder();
        builder.valueType = copy.getValueType();
        builder.errors = copy.getErrors();
        builder.completedValue = copy.getCompletedValue();
        builder.fetchedValue = copy.getFetchedValue();
        builder.children = copy.getChildren();
        builder.nullValue = copy.isNullValue();
        builder.field = copy.getField();
        builder.name = copy.getName();
        builder.fieldSubSelection = copy.fieldSubSelection;
        builder.executionInfo = copy.getExecutionInfo();
        return builder;
    }

    public FetchedValueType getValueType() {
        return valueType;
    }

    public void setValueType(FetchedValueType valueType) {
        this.valueType = valueType;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public void setErrors(List<GraphQLError> errors) {
        this.errors = errors;
    }

    public Object getCompletedValue() {
        return completedValue;
    }

    public void setCompletedValue(Object completedValue) {
        this.completedValue = completedValue;
    }

    public List<FetchedValueAnalysis> getChildren() {
        return children;
    }

    public void setChildren(List<FetchedValueAnalysis> children) {
        this.children = children;
    }

    public boolean isNullValue() {
        return nullValue;
    }

    public void setNullValue(boolean nullValue) {
        this.nullValue = nullValue;
    }


    public FetchedValue getFetchedValue() {
        return fetchedValue;
    }

    public void setFetchedValue(FetchedValue fetchedValue) {
        this.fetchedValue = fetchedValue;
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static Builder newFetchedValueAnalysis() {
        return new Builder();
    }

    public static Builder newFetchedValueAnalysis(FetchedValueType valueType) {
        return new Builder().valueType(valueType);
    }

    public ExecutionInfo getExecutionInfo() {
        return executionInfo;
    }

    public void setExecutionInfo(ExecutionInfo executionInfo) {
        this.executionInfo = executionInfo;
    }

    public FieldSubSelection getFieldSubSelection() {
        return fieldSubSelection;
    }

    public void setFieldSubSelection(FieldSubSelection fieldSubSelection) {
        this.fieldSubSelection = fieldSubSelection;
    }

    @Override
    public String toString() {
        return "FetchedValueAnalysis{" +
                "valueType=" + valueType +
                ", errors=" + errors +
                ", completedValue=" + completedValue +
                ", children=" + children +
                ", nullValue=" + nullValue +
                ", field=" + field +
                ", name='" + name + '\'' +
                ", fieldSubSelection=" + fieldSubSelection +
                ", executionInfo=" + executionInfo +
                ", fetchedValue=" + fetchedValue +
                '}';
    }

    public static final class Builder {
        private FetchedValueType valueType;
        private List<GraphQLError> errors = new ArrayList<>();
        private Object completedValue;
        private FetchedValue fetchedValue;
        private List<FetchedValueAnalysis> children;
        private FieldSubSelection fieldSubSelection;
        private boolean nullValue;
        private Field field;
        private String name;
        private ExecutionInfo executionInfo;

        private Builder() {
        }


        public Builder valueType(FetchedValueType val) {
            valueType = val;
            return this;
        }

        public Builder errors(List<GraphQLError> val) {
            errors = val;
            return this;
        }

        public Builder error(GraphQLError val) {
            errors.add(val);
            return this;
        }


        public Builder completedValue(Object val) {
            completedValue = val;
            return this;
        }

        public Builder fetchedValue(FetchedValue val) {
            fetchedValue = val;
            return this;
        }

        public Builder children(List<FetchedValueAnalysis> val) {
            children = val;
            return this;
        }


        public Builder nullValue() {
            nullValue = true;
            return this;
        }

        public Builder field(Field val) {
            field = val;
            return this;
        }

        public Builder name(String val) {
            name = val;
            return this;
        }

        public Builder fieldSubSelection(FieldSubSelection fieldSubSelection) {
            this.fieldSubSelection = fieldSubSelection;
            return this;
        }

        public Builder executionInfo(ExecutionInfo executionInfo) {
            this.executionInfo = executionInfo;
            return this;
        }

        public FetchedValueAnalysis build() {
            return new FetchedValueAnalysis(this);
        }
    }
}
