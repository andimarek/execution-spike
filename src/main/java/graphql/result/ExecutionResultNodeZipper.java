package graphql.result;

import java.util.List;

public class ExecutionResultNodeZipper {

    private final ExecutionResultNode curNode;
    private final List<Breadcrumb> breadcrumbList;

    public ExecutionResultNodeZipper(ExecutionResultNode curNode, List<Breadcrumb> breadcrumbs) {
        this.curNode = curNode;
        this.breadcrumbList = breadcrumbs;
    }

    public ExecutionResultNode getCurNode() {
        return curNode;
    }

    @Override
    public String toString() {
        return "ExecutionResultNodeZipper{" +
                "curNode=" + curNode +
                ", breadcrumbList=" + breadcrumbList +
                '}';
    }
}
