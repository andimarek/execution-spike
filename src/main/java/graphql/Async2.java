package graphql;

import graphql.execution.Async;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Async2 {


    public static <U, T> CompletableFuture<U> reduce(List<CompletableFuture<T>> values, U initialValue, BiFunction<U, T, U> aggregator) {
        CompletableFuture<U> result = new CompletableFuture<>();
        reduceImpl(values, 0, initialValue, aggregator, result);
        return result;
    }

    public static <U, T> CompletableFuture<U> reduce(CompletableFuture<List<T>> values, U initialValue, BiFunction<U, T, U> aggregator) {
        return values.thenApply(list -> {
            U result = initialValue;
            for (T value : list) {
                result = aggregator.apply(result, value);
            }
            return result;
        });
    }

    public static <U, T> CompletableFuture<List<U>> flatMap(List<T> inputs, Function<T, CompletableFuture<U>> mapper) {
        List<CompletableFuture<U>> collect = inputs
                .stream()
                .map(mapper)
                .collect(Collectors.toList());
        return Async.each(collect);
    }

    private static <U, T> void reduceImpl(List<CompletableFuture<T>> values, int curIndex, U curValue, BiFunction<U, T, U> aggregator, CompletableFuture<U> result) {
        if (curIndex == values.size()) {
            result.complete(curValue);
            return;
        }
        values.get(curIndex).
                thenApply(oneValue -> aggregator.apply(curValue, oneValue))
                .thenAccept(newValue -> reduceImpl(values, curIndex + 1, newValue, aggregator, result));
    }

    public static <U, T> CompletableFuture<List<U>> map(CompletableFuture<List<T>> values, Function<T, U> mapper) {
        return values.thenApply(list -> list.stream().map(mapper).collect(Collectors.toList()));
    }


}
