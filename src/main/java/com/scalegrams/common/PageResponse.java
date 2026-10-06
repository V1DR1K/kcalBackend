package com.scalegrams.common;

import java.util.List;

import org.springframework.data.domain.Page;

/** Shared paginated response contract for API collections. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }
}
