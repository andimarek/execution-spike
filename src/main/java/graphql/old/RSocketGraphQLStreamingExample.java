package graphql.old;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Common;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.rsocket.AbstractRSocket;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public class RSocketGraphQLStreamingExample {

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        RSocketFactory.receive()
                .acceptor(new SocketAcceptorImpl())
                .transport(TcpServerTransport.create("localhost", 7000))
                .start()
                .subscribe();

        RSocket socket =
                RSocketFactory.connect()
                        .transport(TcpClientTransport.create("localhost", 7000))
                        .start()
                        .block();

        socket
                .requestStream(DefaultPayload.create("{foo{id bar{id name}}}"))
                .map(Payload::getDataUtf8)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, ResultLeaf.class);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                })
                .reduce(new ConcurrentHashMap<>(), (result, resultLeaf) -> {
                    Common.insertValueInResult(resultLeaf, result);
                    return result;
                })
                .doOnNext(result -> {
                    System.out.println("Result: " + result);
                })
                .then()
                .doFinally(signalType -> socket.dispose())
                .then()
                .block();
    }

    private static class SocketAcceptorImpl implements SocketAcceptor {

        private ObjectMapper objectMapper;
        GraphQLSchema schema;
        ReactorStreamingExecution reactorStreamingExecution;

        public SocketAcceptorImpl() {
            this.schema = createSchema();
            this.reactorStreamingExecution = new ReactorStreamingExecution();
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public Mono<RSocket> accept(ConnectionSetupPayload setupPayload, RSocket reactiveSocket) {
            return Mono.just(
                    new AbstractRSocket() {
                        @Override
                        public Flux<Payload> requestStream(Payload payload) {
                            String query = payload.getDataUtf8();
                            return executeQuery(query).map(resultLeaf -> {
                                try {
                                    return DefaultPayload.create(objectMapper.writeValueAsString(resultLeaf));
                                } catch (JsonProcessingException e) {
                                    e.printStackTrace();
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                    });
        }

        Flux<ResultLeaf> executeQuery(String query) {
            return this.reactorStreamingExecution.execute(query, this.schema);
        }
    }

    private static GraphQLSchema createSchema() {
        ImmutableMap<String, Serializable> fooData = ImmutableMap.of("id", "fooId",
                "bar", ImmutableList.of(
                        ImmutableMap.of("id", "barId", "name", "someBar1"),
                        ImmutableMap.of("id", "barId2", "name", "someBar2")
                ));
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("foo", env -> fooData))
                .build();

        String sdl = "type Query{foo: Foo} type Foo{id:ID, bar:[Bar]} type Bar{id:String, name:String}";

        GraphQLSchema schema = createSchema(sdl, runtimeWiring);
        return schema;
    }

    private static GraphQLSchema createSchema(String schema, RuntimeWiring runtimeWiring) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        SchemaGenerator.Options options = SchemaGenerator.Options.defaultOptions();
        return new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring);
    }

}
