package com.uscdip.backend.dto;

public record AccountRoleOptionResponse(
        String roleCode,
        String roleName,
        String description,
        boolean readOnly
) {
}
