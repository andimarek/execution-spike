package graphql;

import graphql.execution.ExecutionPath;
import graphql.language.Field;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;

public class AbsoluteGraphQLError implements GraphQLError {

    private final List<SourceLocation> locations;
    private final List<Object> absolutePath;
    private final String message;
    private final ErrorType errorType;
    private final Map<String, Object> extensions;

    public AbsoluteGraphQLError(List<Field> field, ExecutionPath executionPath, GraphQLError relativeError) {
        assertNotNull(relativeError);
        this.absolutePath = createAbsolutePath(executionPath, relativeError);
        this.locations = createAbsoluteLocations(relativeError, field);
        this.message = relativeError.getMessage();
        this.errorType = relativeError.getErrorType();
        if (relativeError.getExtensions() != null) {
            this.extensions = new HashMap<>();
            this.extensions.putAll(relativeError.getExtensions());
        } else {
            this.extensions = null;
        }
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public List<Object> getPath() {
        return absolutePath;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    private List<Object> createAbsolutePath(ExecutionPath executionPath,
                                            GraphQLError relativeError) {
        return Optional.ofNullable(relativeError.getPath())
                .map(originalPath -> {
                    List<Object> path = new ArrayList<>();
                    path.addAll(executionPath.toList());
                    path.addAll(relativeError.getPath());
                    return path;
                })
                .map(Collections::unmodifiableList)
                .orElse(null);
    }

    private List<SourceLocation> createAbsoluteLocations(GraphQLError relativeError, List<Field> fields) {
        Optional<SourceLocation> baseLocation;
        if (!fields.isEmpty()) {
            baseLocation = Optional.ofNullable(fields.get(0).getSourceLocation());
        } else {
            baseLocation = Optional.empty();
        }

        // relative error empty path should yield an absolute error with the base path
        if (relativeError.getLocations() != null && relativeError.getLocations().isEmpty()) {
            return baseLocation.map(Collections::singletonList).orElse(null);
        }

        return Optional.ofNullable(
                relativeError.getLocations())
                .map(locations -> locations.stream()
                        .map(l ->
                                baseLocation
                                        .map(base -> new SourceLocation(
                                                base.getLine() + l.getLine(),
                                                base.getColumn() + l.getColumn()))
                                        .orElse(null))
                        .collect(Collectors.toList()))
                .map(Collections::unmodifiableList)
                .orElse(null);
    }
}
