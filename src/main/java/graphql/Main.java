package graphql;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Mono<Integer>> list = Arrays.asList(Mono.just(1), Mono.empty(), Mono.just(3));

//        Flux.merge(list).map(integer -> {
//            System.out.println("mapping value " + integer);
//            return integer;
//        }).subscribe();
        Flux.merge(list).map(integer -> {
            System.out.println("mapping value " + integer);
            return integer;
        }).subscribe();
    }
}
