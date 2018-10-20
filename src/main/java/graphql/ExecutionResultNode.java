package graphql;

import graphql.execution.NonNullableFieldWasNullException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionResultNode {

    //    private IndexOrName indexOrName;
//    private Object leafValue;
    private final FetchedValueAnalysis fetchedValueAnalysis;
    private final NonNullableFieldWasNullException nonNullableFieldWasNullException;
    private final List<ExecutionResultNode> children;

    public ExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis, NonNullableFieldWasNullException nonNullableFieldWasNullException, List<ExecutionResultNode> children) {
        this.fetchedValueAnalysis = fetchedValueAnalysis;
        this.nonNullableFieldWasNullException = nonNullableFieldWasNullException;
        this.children = children;
    }

    public ExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis, NonNullableFieldWasNullException nonNullableFieldWasNullException) {
        this(fetchedValueAnalysis, nonNullableFieldWasNullException, Collections.emptyList());
    }

    public FetchedValueAnalysis getFetchedValueAnalysis() {
        return fetchedValueAnalysis;
    }

    public NonNullableFieldWasNullException getNonNullableFieldWasNullException() {
        return nonNullableFieldWasNullException;
    }

    public List<ExecutionResultNode> getChildren() {
        return new ArrayList<>(children);
    }

    public ExecutionResultNode withChildren(List<ExecutionResultNode> newChildren) {
        return new ExecutionResultNode(this.fetchedValueAnalysis, this.nonNullableFieldWasNullException, newChildren);
    }
}
