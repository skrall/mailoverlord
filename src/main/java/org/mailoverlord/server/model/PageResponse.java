package org.mailoverlord.server.model;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A page of results, including the metadata a client needs to render pagination controls.
 *
 * Spring's own {@code Page} serialises with an unstable shape across versions, so the
 * response is a fixed record rather than the framework type.
 */
public record PageResponse<T>(
        List<T> content,
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
