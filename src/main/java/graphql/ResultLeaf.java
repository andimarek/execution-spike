package graphql;

import graphql.execution.ExecutionPath;

public class ResultLeaf {
    private final ExecutionPath executionPath;
    private final Object value;

    public ResultLeaf(ExecutionPath executionPath, Object value) {
        this.executionPath = executionPath;
        this.value = value;
    }

    public ExecutionPath getExecutionPath() {
        return executionPath;
    }

    public Object getValue() {
        return value;
    }


    @Override
    public String toString() {
        return "ResultLeaf{" +
                "executionPath=" + executionPath +
                ", value=" + value +
                '}';
    }
}
