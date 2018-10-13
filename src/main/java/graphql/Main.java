package graphql;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        List<Integer> ints = new ArrayList<>();
        ints.add(1);
        ints.add(2);
        ints.add(3);
        ints.add(4);
        ints.add(5);
        List<Mono<Integer>> listElements = ints.stream().map(value -> Mono.just(value)).collect(Collectors.toList());

        Mono<Integer> reduced = Flux.merge(listElements).reduce(0, (integer, integer2) -> {
            return integer + integer2;
        });
        reduced.subscribe(integer -> {
            System.out.printf("" + integer);
        });
    }
}
