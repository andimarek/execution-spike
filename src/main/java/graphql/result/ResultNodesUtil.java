package graphql.result;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import static graphql.result.ExecutionResultNodePosition.index;
import static graphql.result.ExecutionResultNodePosition.key;

public class ResultNodesUtil {

    public static List<ExecutionResultNodeZipper> getUnresolvedNodes(Collection<ExecutionResultNode> roots) {
        List<ExecutionResultNodeZipper> result = new ArrayList<>();

        ResultNodeTraverser resultNodeTraverser = new ResultNodeTraverser(new ResultNodeVisitor() {
            @Override
            public void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs) {
                if (node instanceof ExecutionResultNode.ObjectExecutionResultNode) {
                    result.add(new ExecutionResultNodeZipper(node, breadcrumbs));
                }
            }
        });
        roots.forEach(resultNodeTraverser::traverse);
        return result;
    }


    public static class ResultNodeVisitor {

        public void visit(ExecutionResultNode node, List<Breadcrumb> breadcrumbs) {

        }
    }

    private static class ResultNodeTraverser {

        ResultNodeVisitor visitor;
        Deque<Breadcrumb> breadCrumbsStack = new ArrayDeque<>();

        public ResultNodeTraverser(ResultNodeVisitor visitor) {
            this.visitor = visitor;
        }

        public void traverse(ExecutionResultNode node) {
            if (node instanceof ExecutionResultNode.ObjectExecutionResultNode) {
                ((ExecutionResultNode.ObjectExecutionResultNode) node).getChildrenMap().forEach((name, child) -> {
                    breadCrumbsStack.push(new Breadcrumb(node, key(name)));
                    traverse(child);
                    breadCrumbsStack.pop();
                });
            }
            if (node instanceof ExecutionResultNode.ListExecutionResultNode) {
                List<ExecutionResultNode> children = node.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    breadCrumbsStack.push(new Breadcrumb(node, index(i)));
                    traverse(children.get(i));
                    breadCrumbsStack.pop();
                }
            }
            List<Breadcrumb> breadcrumbs = new ArrayList<>();
            Iterator<Breadcrumb> breadcrumbIterator = breadCrumbsStack.descendingIterator();
            breadcrumbIterator.forEachRemaining(breadcrumbs::add);
            visitor.visit(node, breadcrumbs);
        }

    }

}
