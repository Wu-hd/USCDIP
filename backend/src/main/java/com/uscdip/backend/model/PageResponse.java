package com.uscdip.backend.model;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int computedTotalPages = (int) Math.ceil((double) total / safePageSize);
        boolean computedHasNext = safePage < computedTotalPages;
        return new PageResponse<>(items, total, safePage, safePageSize, computedTotalPages, computedHasNext);
    }
}
