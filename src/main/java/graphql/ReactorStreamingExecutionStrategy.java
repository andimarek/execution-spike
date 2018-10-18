package graphql;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.language.Field;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

import static graphql.ValueFetcher.NULL_VALUE;

public class ReactorStreamingExecutionStrategy {

    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public ReactorStreamingExecutionStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    public Flux<ResultLeaf> execute(FieldSubSelection fieldSubSelection) {
        return Flux.create(sink -> executeImpl(fieldSubSelection, sink, true));
    }

    private void executeImpl(FieldSubSelection fieldSubSelection, FluxSink<ResultLeaf> sink, boolean firstLevel) {
        fetchAndAnalyze(fieldSubSelection)
                .subscribe(fetchedValueAnalysis -> convertFetchedValue(fetchedValueAnalysis, sink),
                        sink::error,
                        () -> {
                            if (firstLevel) {
                                sink.complete();
                            }
                        });
    }

    private void convertFetchedValue(FetchedValueAnalysis fetchedValueAnalysis, FluxSink<ResultLeaf> sink) {
        if (fetchedValueAnalysis.isNullValue()) {
            sink.next(new ResultLeaf(fetchedValueAnalysis.getExecutionStepInfo().getPath(), NULL_VALUE));
            return;
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.OBJECT) {
            FieldSubSelection nextLevelSubSelection = fetchedValueAnalysis.getFieldSubSelection();
            executeImpl(nextLevelSubSelection, sink, false);
            return;
        }
        if (fetchedValueAnalysis.getValueType() == FetchedValueAnalysis.FetchedValueType.LIST) {
            fetchedValueAnalysis
                    .getChildren()
                    .forEach(fetchedValueAnalysis1 -> convertFetchedValue(fetchedValueAnalysis1, sink));
            return;
        }
        ResultLeaf resultLeaf = new ResultLeaf(fetchedValueAnalysis.getExecutionStepInfo().getPath(), fetchedValueAnalysis.getCompletedValue());
        sink.next(resultLeaf);
    }


    private Flux<FetchedValueAnalysis> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<Mono<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fieldSubSelection.getExecutionStepInfo());
                    return valueFetcher
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
                            .map(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
                })
                .collect(Collectors.toList());
        return Flux.merge(fetchedValues);
    }

    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }


}
