package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DepthValidationRequest {

    @JsonProperty("node_id")
    @JsonAlias({"nodeId", "nodeid"})
    private String nodeId;

    @JsonProperty("z_top")
    @JsonAlias({"zTop", "ztop"})
    @NotNull(message = "z_top is required")
    private BigDecimal zTop;

    @JsonProperty("z_bottom")
    @JsonAlias({"zBottom", "zbottom"})
    @NotNull(message = "z_bottom is required")
    private BigDecimal zBottom;

    @JsonProperty("bury_depth")
    @JsonAlias({"buryDepth", "burydepth"})
    @NotNull(message = "bury_depth is required")
    private BigDecimal buryDepth;

    @JsonProperty("elevation_ref")
    @JsonAlias({"elevationRef", "elevationref"})
    private String elevationRef;

    @JsonProperty("tolerance")
    private BigDecimal tolerance;
}
