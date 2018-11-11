package graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public interface BatchedDataFetcher<T> extends DataFetcher<T> {
    Iterable<T> getBatched(DataFetchingEnvironment environment) throws Exception;
}
