package graphql.result;

import graphql.Assert;
import graphql.FetchedValueAnalysis;
import graphql.execution.NonNullableFieldWasNullException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public abstract ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position);

    public abstract ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children);


    public static class LeafExecutionResultNode extends ExecutionResultNode {

        public LeafExecutionResultNode(FetchedValueAnalysis fetchedValueAnalysis,
                                       NonNullableFieldWasNullException nonNullableFieldWasNullException) {
            super(fetchedValueAnalysis, nonNullableFieldWasNullException);
        }

        @Override
        public List<ExecutionResultNode> getChildren() {
            return null;
        }

        @Override
        public ExecutionResultNode withChild(ExecutionResultNode child, ExecutionResultNodePosition position) {
            return Assert.assertShouldNeverHappen("Not available for leafs");
        }

        @Override
        public ExecutionResultNode withNewChildren(Map<ExecutionResultNodePosition, ExecutionResultNode> children) {
            return Assert.assertShouldNeverHappen();
        }

        public Object getValue() {
            return getFetchedValueAnalysis().getCompletedValue();
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

        if (root instanceof ObjectExecutionResultNode.UnresolvedObjectResultNode) {
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
