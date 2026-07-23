package com.uscdip.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TopicAuthorizationRequest(
        @Schema(description = "用户ID", example = "U-B07-HZ-001")
        @NotBlank(message = "userId is required")
        String userId,

        @Schema(description = "入口权限码", example = "ENTRY:EMGC")
        String entryPermission,

        @Schema(description = "菜单权限码", example = "MENU:WORKORDER:READ")
        String menuPermission,

        @Schema(description = "区域ID", example = "REGION-HZ")
        String regionId,

        @Schema(description = "任务处理人", example = "zhangsan")
        String assignee,

        @Schema(description = "数据视图类型", example = "AGGREGATED")
        String dataView,

        @Schema(description = "请求的 topic 列表")
        @NotEmpty(message = "requestedTopics must not be empty")
        List<String> requestedTopics
) {
}
