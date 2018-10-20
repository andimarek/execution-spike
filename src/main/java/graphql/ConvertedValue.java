package graphql;

import graphql.execution.ExecutionStepInfo;

public class ConvertedValue {

    // scalar or list of scalar or list of list of scalar ...  or
    // object or list of object or list of list object ....
    // or error (NonNullableFieldWasNullException)

    private Object value;
    private ExecutionStepInfo executionStepInfo;

}
