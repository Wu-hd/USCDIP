package com.uscdip.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthzCheckRequest {

    @NotBlank(message = "userId is required")
    private String userId;
    private String entryPermission;
    private String menuPermission;
    private String topic;
    private String regionId;
    private String assignee;
    private String dataView;
}
