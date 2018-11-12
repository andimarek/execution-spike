package graphql.result;

import java.util.List;

public class ExecutionResultNodeZipper {

    private final ExecutionResultNode curNode;
    // from curNode upwards
    private final List<Breadcrumb> breadcrumbList;

    public ExecutionResultNodeZipper(ExecutionResultNode curNode, List<Breadcrumb> breadcrumbs) {
        this.curNode = curNode;
        this.breadcrumbList = breadcrumbs;
    }

    public ExecutionResultNode getCurNode() {
        return curNode;
    }

    public ExecutionResultNodeZipper withNode(ExecutionResultNode newNode) {
        return new ExecutionResultNodeZipper(newNode, breadcrumbList);
    }

    public ExecutionResultNode toRootNode() {
        ExecutionResultNode curRoot = curNode;
        for (Breadcrumb breadcrumb : breadcrumbList) {
            curRoot = breadcrumb.node.withChild(curRoot, breadcrumb.position);
        }
        return curRoot;
    }


    @Override
    public String toString() {
        return "ExecutionResultNodeZipper{" +
                "curNode=" + curNode +
                ", breadcrumbList=" + breadcrumbList +
                '}';
    }
}