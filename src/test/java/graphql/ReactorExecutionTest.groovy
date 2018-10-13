package graphql

import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import spock.lang.Specification

class ReactorExecutionTest extends Specification {


    def "test execution"() {
        def fooData = [id: "fooId", bar: [id: "barId", name: "someFoo"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            id: ID
            bar: Bar
        }    
        type Bar {
            id: ID
            name: String
        }
        """, dataFetchers)


        def document = graphql.TestUtil.parseQuery("""
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


        ReactorExecution reactorExecution = new ReactorExecution();

        when:
        def monoResult = reactorExecution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.toFuture().get()


        then:
        result.getData() == [foo: fooData]


    }
}
