package com.uscdip.backend.dto;

import java.util.List;

public record AccountPageResponse(
        List<AccountResponse> items,
        long total,
        int page,
        int pageSize,
        int totalPages,
        boolean hasNext,
        long activeCount,
        long disabledCount,
        long platformAdminCount
) {
}
