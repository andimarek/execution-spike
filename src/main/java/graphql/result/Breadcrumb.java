package graphql.result;

public class Breadcrumb {
    public ExecutionResultNode node;
    public ExecutionResultNodePosition position;

    public Breadcrumb(ExecutionResultNode node, ExecutionResultNodePosition position) {
        this.node = node;
        this.position = position;
    }

    @Override
    public String toString() {
        return "Breadcrumb{" +
                "node=" + node +
                ", position=" + position +
                '}';
    }
}