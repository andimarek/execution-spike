package graphql;

import graphql.execution.NonNullableFieldWasNullException;
import graphql.result.ExecutionResultNode;
import graphql.result.ListExecutionResultNode;
import graphql.result.ObjectExecutionResultNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class ResultNodesCreator {

    public ExecutionResultNode createResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        if (fetchedValueAnalysis.isNullValue() && fetchedValueAnalysis.getExecutionStepInfo().isNonNullType()) {
            NonNullableFieldWasNullException nonNullableFieldWasNullException =
                    new NonNullableFieldWasNullException(fetchedValueAnalysis.getExecutionStepInfo(), fetchedValueAnalysis.getExecutionStepInfo().getPath());
            return new ExecutionResultNode.LeafExecutionResultNode(fetchedValueAnalysis, nonNullableFieldWasNullException);
        }
        if (fetchedValueAnalysis.isNullValue()) {
            return new ExecutionResultNode.LeafExecutionResultNode(fetchedValueAnalysis, null);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.OBJECT) {
            return createObjectResultNode(fetchedValueAnalysis);
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
            return createListResultNode(fetchedValueAnalysis);
        }
        return new ExecutionResultNode.LeafExecutionResultNode(fetchedValueAnalysis, null);
    }

    private ExecutionResultNode createObjectResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        return new ObjectExecutionResultNode.UnresolvedObjectResultNode(fetchedValueAnalysis);
    }

    private Optional<NonNullableFieldWasNullException> getFirstNonNullableException(Collection<ExecutionResultNode> collection) {
        return collection.stream()
                .filter(executionResultNode -> executionResultNode.getNonNullableFieldWasNullException() != null)
                .map(ExecutionResultNode::getNonNullableFieldWasNullException)
                .findFirst();
    }

    private ExecutionResultNode createListResultNode(FetchedValueAnalysis fetchedValueAnalysis) {
        List<ExecutionResultNode> executionResultNodes = fetchedValueAnalysis
                .getChildren()
                .stream()
                .map(this::createResultNode)
                .collect(toList());
        boolean listIsNonNull = fetchedValueAnalysis.getExecutionStepInfo().isNonNullType();
        if (listIsNonNull) {
            Optional<NonNullableFieldWasNullException> subException = getFirstNonNullableException(executionResultNodes);
            if (subException.isPresent()) {
                NonNullableFieldWasNullException listException = new NonNullableFieldWasNullException(subException.get());
                return new ListExecutionResultNode(fetchedValueAnalysis, listException, executionResultNodes);
            }
        }
        return new ListExecutionResultNode(fetchedValueAnalysis, null, executionResultNodes);
    }
}
