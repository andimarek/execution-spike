package graphql;

import java.util.Collections;
import java.util.List;

public class ExecutionResultNodeZipper {

    public static class Breadcrumb {
        private ExecutionResultNode node;
        private Object position;
    }

    private final ExecutionResultNode curNode;
    private final List<Breadcrumb> breadcrumbList;

    public ExecutionResultNodeZipper(ExecutionResultNode curNode, List<Breadcrumb> breadcrumbList) {
        this.curNode = curNode;
        this.breadcrumbList = breadcrumbList;
    }

    public static ExecutionResultNodeZipper newZipper(ExecutionResultNode node, ExecutionResultNode parent, Object position) {
        return new ExecutionResultNodeZipper(node, Collections.emptyList());
    }

    public static ExecutionResultNodeZipper newZipper(ExecutionResultNode node, ExecutionResultNodeZipper parent, Object position) {
        return new ExecutionResultNodeZipper(node, Collections.emptyList());
    }

    public ExecutionResultNode getCurNode() {
        return curNode;
    }
}
