package com.uscdip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthzCheckResult {

    private String userId;
    private List<String> roleCodes;
    private boolean entryPermission;
    private boolean menuPermission;
    private boolean dataScope;
    private boolean topicScope;
    private boolean allowed;
    private String reason;
}
