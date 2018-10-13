package graphql;

import graphql.execution.ExecutionInfo;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * A map from name to List of Field representing the actual sub selections (during execution) of a Field with Fragments
 * evaluated and conditional directives considered.
 */
public class FieldSubSelection {


    private Object source;
    // the type of this must be objectType
    private ExecutionInfo executionInfo;
    private Map<String, List<Field>> fields = new LinkedHashMap<>();


    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public Map<String, List<Field>> getFields() {
        return fields;
    }

    public void setFields(Map<String, List<Field>> fields) {
        this.fields = fields;
    }

    public ExecutionInfo getExecutionInfo() {
        return executionInfo;
    }

    public void setExecutionInfo(ExecutionInfo executionInfo) {
        this.executionInfo = executionInfo;
    }
}
