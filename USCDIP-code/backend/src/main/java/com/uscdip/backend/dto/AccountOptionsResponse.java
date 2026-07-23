package com.uscdip.backend.dto;

import java.util.List;

public record AccountOptionsResponse(
        List<AccountRoleOptionResponse> roles,
        List<String> statuses,
        List<String> scopeTypes
) {
}
