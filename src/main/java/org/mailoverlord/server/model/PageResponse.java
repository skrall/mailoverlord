package org.mailoverlord.server.model;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A page of results, including the metadata a client needs to render pagination controls.
 *
 * Spring's own {@code Page} serialises with an unstable shape across versions, so the
 * response is a fixed record rather than the framework type.
 *
 * <p>The element type is pinned to {@link MessageSummary} because this is the only page the
 * API returns, and the OpenAPI generator erases type parameters. Documenting the responses
 * explicitly is what forces the schema to be written here rather than inferred from the
 * controller's return type; without it the generated {@code content} would be an untyped array.
 */
public record PageResponse<T>(
        @ArraySchema(schema = @Schema(implementation = MessageSummary.class)) List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
