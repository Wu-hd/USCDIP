package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DepthValidationRequest {

    @JsonProperty("node_id")
    @JsonAlias({"nodeId", "nodeid"})
    @Schema(description = "节点ID", example = "NODE-001")
    private String nodeId;

    @JsonProperty("z_top")
    @JsonAlias({"zTop", "ztop"})
    @Schema(description = "顶部高程", example = "2.50")
    @NotNull(message = "zTop is required")
    private BigDecimal zTop;

    @JsonProperty("z_bottom")
    @JsonAlias({"zBottom", "zbottom"})
    @Schema(description = "底部高程", example = "-1.20")
    @NotNull(message = "zBottom is required")
    private BigDecimal zBottom;

    @JsonProperty("bury_depth")
    @JsonAlias({"buryDepth", "burydepth"})
    @Schema(description = "埋深", example = "3.70")
    @NotNull(message = "buryDepth is required")
    private BigDecimal buryDepth;

    @JsonProperty("elevation_ref")
    @JsonAlias({"elevationRef", "elevationref"})
    @Schema(description = "高程基准", example = "MSL")
    private String elevationRef;

    @JsonProperty("tolerance")
    @Schema(description = "容差", example = "0.05")
    private BigDecimal tolerance;
}
