package graphql.result;

import graphql.FetchedValueAnalysis;
import graphql.execution.NonNullableFieldWasNullException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ObjectExecutionResultNode extends ExecutionResultNode {

    private Map<String, ExecutionResultNode> children;

    public ObjectExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                     NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                     Map<String, ExecutionResultNode> children) {
        super(fetchedValueAnalysis, nonNullableFieldWasNullException);
        this.children = children;
    }

    @Override
    public List<ExecutionResultNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    @Override
    public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
        LinkedHashMap<String, ExecutionResultNode> newChildren = new LinkedHashMap<>(this.children);
        newChildren.put(position.getKey(), child);
        return new graphql.result.ObjectExecutionResultNode(getFetchedValueAnalysis(), getNonNullableFieldWasNullException(), newChildren);
    }

    @Override
    public ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children) {
        LinkedHashMap<String, ExecutionResultNode> mergedChildren = new LinkedHashMap<>(this.children);
        children.entrySet().stream().forEach(entry -> mergedChildren.put(entry.getKey().getKey(), entry.getValue()));
        return new graphql.result.ObjectExecutionResultNode(getFetchedValueAnalysis(), getNonNullableFieldWasNullException(), mergedChildren);
    }

    public Map<String, ExecutionResultNode> getChildrenMap() {
        return new LinkedHashMap<>(children);
    }

    public Optional<NonNullableFieldWasNullException> getChildrenNonNullableException() {
        return children.values().stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    public graphql.result.ObjectExecutionResultNode withChildren(Map<String, ExecutionResultNode> children) {
        return new graphql.result.ObjectExecutionResultNode(getFetchedValueAnalysis(), getNonNullableFieldWasNullException(), children);
    }

    public static class UnresolvedObjectResultNode extends ObjectExecutionResultNode {

        public UnresolvedObjectResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
            super(fetchedValueAnalysis, null, Collections.emptyMap());
        }

        @Override
        public String toString() {
            return "UnresolvedObjectResultNode{" +
                    "fetchedValueAnalysis=" + getFetchedValueAnalysis() +
                    '}';
        }
    }

    public static class RootExecutionResultNode extends ObjectExecutionResultNode {

        public RootExecutionResultNode(Map<String, ExecutionResultNode> children) {
            super(null, null, children);
        }

        @Override
        public FetchedValueAnalysis getFetchedValueAnalysis() {
            throw new RuntimeException("Root node");
        }

        @Override
        public ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children) {
            LinkedHashMap<String, ExecutionResultNode> mergedChildren = new LinkedHashMap<>(getChildrenMap());
            children.entrySet().stream().forEach(entry -> mergedChildren.put(entry.getKey().getKey(), entry.getValue()));
            return new graphql.result.ObjectExecutionResultNode.RootExecutionResultNode(mergedChildren);
        }

        @Override
        public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
            LinkedHashMap<String, ExecutionResultNode> newChildren = new LinkedHashMap<>(getChildrenMap());
            newChildren.put(position.getKey(), child);
            return new graphql.result.ObjectExecutionResultNode.RootExecutionResultNode(newChildren);
        }
    }

}
