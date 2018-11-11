package graphql.result;

public class ExecutionResultNodePosition {

    private Integer position;
    private String key;

    public ExecutionResultNodePosition(Integer position) {
        this.position = position;
    }

    public ExecutionResultNodePosition(String key) {
        this.key = key;
    }

    public static ExecutionResultNodePosition index(int position) {
        return new ExecutionResultNodePosition(position);
    }

    public static ExecutionResultNodePosition key(String key) {
        return new ExecutionResultNodePosition(key);
    }

    @Override
    public String toString() {
        return position != null ? position.toString() : key;
    }
}
