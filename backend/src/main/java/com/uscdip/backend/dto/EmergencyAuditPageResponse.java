package com.uscdip.backend.dto;

import java.util.List;

public record EmergencyAuditPageResponse(
        List<EmergencyAuditRecordResponse> records,
        long total,
        int page,
        int pageSize
) {
}
