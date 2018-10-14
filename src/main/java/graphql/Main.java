package graphql;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Mono<Object>> list = Arrays.asList(Mono.just(1), Mono.error(new RuntimeException()), Mono.just(3));

//        Flux.merge(list).map(integer -> {
//            System.out.println("mapping value " + integer);
//            return integer;
//        }).subscribe();
        Mono<Object> monoList = Flux.merge(list)
//                .onErrorResume(RuntimeException.class, e -> {
//                    return Mono.just(ValueFetcher.NULL_VALUE);
//                }).map(Object.class::cast)
                .collectList().map(Object.class::cast).onErrorResume(exception -> {
                    System.out.println("Exception");
                    return Mono.just(ValueFetcher.NULL_VALUE);
                });
        monoList.subscribe(objects -> {
            System.out.printf("objects:" + objects);
        });
    }
}
