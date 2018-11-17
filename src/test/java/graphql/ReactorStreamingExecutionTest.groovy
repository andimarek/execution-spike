package graphql

import graphql.execution.ExecutionId
import graphql.old.ReactorStreamingExecution
import graphql.schema.DataFetcher
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap

import static org.awaitility.Awaitility.await

class ReactorStreamingExecutionTest extends Specification {

    def "test execution"() {
        def fooData = [id: "fooId", bar: [[id: "barId1", name: "someBar1"], [id: "barId2", name: "someBar2"]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            id: ID
            bar: [Bar]
        }    
        type Bar {
            id: ID
            name: String
        }
        """, dataFetchers)


        def document = TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()

        ReactorStreamingExecution execution = new ReactorStreamingExecution()

        when:
        def resultFlux = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        Map result = new ConcurrentHashMap();

        resultFlux.subscribe({ leaf ->
            Common.insertValueInResult(leaf, result)
        })

        then:
        await().until({ result == [foo: fooData] })

    }
}
