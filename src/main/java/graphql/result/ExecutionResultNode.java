package graphql.result;

import graphql.FetchedValueAnalysis;
import graphql.execution.NonNullableFieldWasNullException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class ExecutionResultNode {

    private final FetchedValueAnalysis fetchedValueAnalysis;
    private final NonNullableFieldWasNullException nonNullableFieldWasNullException;

    public ExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis, NonNullableFieldWasNullException nonNullableFieldWasNullException) {
        this.fetchedValueAnalysis = fetchedValueAnalysis;
        this.nonNullableFieldWasNullException = nonNullableFieldWasNullException;
    }

    public FetchedValueAnalysis getFetchedValueAnalysis() {
        return fetchedValueAnalysis;
    }

    public NonNullableFieldWasNullException getNonNullableFieldWasNullException() {
        return nonNullableFieldWasNullException;
    }

    public abstract List<ExecutionResultNode> getChildren();


    public static class ObjectExecutionResultNode extends ExecutionResultNode {

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

        public Map<String, ExecutionResultNode> getChildrenMap() {
            return new LinkedHashMap<>(children);
        }

        public Optional<NonNullableFieldWasNullException> getChildrenNonNullableException() {
            return children.values().stream()
                    .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                    .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                    .findFirst();
        }
    }

    public static class ListExecutionResultNode extends ExecutionResultNode {

        private List<ExecutionResultNode> children;

        public ListExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                       NonNullableFieldWasNullException nonNullableFieldWasNullException,
                                       List<ExecutionResultNode> children) {
            super(fetchedValueAnalysis, nonNullableFieldWasNullException);
            this.children = children;
        }

        public Optional<NonNullableFieldWasNullException> getChildNonNullableException() {
            return children.stream()
                    .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                    .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                    .findFirst();
        }

        @Override
        public List<ExecutionResultNode> getChildren() {
            return children;
        }
    }

    public static class LeafExecutionResultNode extends ExecutionResultNode {

        public LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                       NonNullableFieldWasNullException nonNullableFieldWasNullException) {
            super(fetchedValueAnalysis, nonNullableFieldWasNullException);
        }

        @Override
        public List<ExecutionResultNode> getChildren() {
            return null;
        }

        public Object getValue() {
            return getFetchedValueAnalysis().getCompletedValue();
        }
    }

    public static class NotResolvedObjectResultNode extends ObjectExecutionResultNode {

        public NotResolvedObjectResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
            super(fetchedValueAnalysis, null, Collections.emptyMap());
        }

        @Override
        public String toString() {
            return "NotResolvedObjectResultNode{" +
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
    }

    public static Object toData(ExecutionResultNode root) {
        if (root instanceof LeafExecutionResultNode) {
            return root.getFetchedValueAnalysis().isNullValue() ? null : ((LeafExecutionResultNode) root).getValue();
        }
        if (root instanceof ListExecutionResultNode) {
            if (((ListExecutionResultNode) root).getChildNonNullableException().isPresent()) {
                return null;
            }
            return root.getChildren().stream().map(ExecutionResultNode::toData).collect(Collectors.toList());
        }

        if (root instanceof NotResolvedObjectResultNode) {
            FetchedValueAnalysis fetchedValueAnalysis = root.getFetchedValueAnalysis();
            return "Not resolved : " + fetchedValueAnalysis.getExecutionStepInfo().getPath() + " with subSelection " + fetchedValueAnalysis.getFieldSubSelection().toShortString();
        }
        if (root instanceof ObjectExecutionResultNode) {
            if (((ObjectExecutionResultNode) root).getChildrenNonNullableException().isPresent()) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            ((ObjectExecutionResultNode) root).getChildrenMap().forEach((key, value) -> result.put(key, toData(value)));
            return result;
        }
        throw new RuntimeException("Unexpected root " + root);
    }
}
