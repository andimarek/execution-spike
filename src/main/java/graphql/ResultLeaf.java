package graphql;

import graphql.execution.ExecutionPath;

import java.util.List;

public class ResultLeaf {
    private List<Object> executionPath;
    private Object value;

    public ResultLeaf() {

    }

    public ResultLeaf(ExecutionPath executionPath, Object value) {
        this.executionPath = executionPath.toList();
        this.value = value;
    }

    public void setExecutionPath(List<Object> executionPath) {
        this.executionPath = executionPath;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public List<Object> getExecutionPath() {
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
