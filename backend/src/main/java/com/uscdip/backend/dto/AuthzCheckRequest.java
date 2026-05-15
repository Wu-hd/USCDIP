package com.uscdip.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "用户ID", example = "U-DISPATCH-001")
    @NotBlank(message = "userId is required")
    private String userId;

    @Schema(description = "入口权限码", example = "ENTRY:EMGC")
    private String entryPermission;

    @Schema(description = "菜单权限码", example = "MENU:WORKORDER:READ")
    private String menuPermission;

    @Schema(description = "订阅 topic", example = "region.REGION-HZ.alerts.critical")
    private String topic;

    @Schema(description = "区域ID", example = "REGION-HZ")
    private String regionId;

    @Schema(description = "任务处理人", example = "zhangsan")
    private String assignee;

    @Schema(description = "数据视图类型", example = "AGGREGATED")
    private String dataView;
}
