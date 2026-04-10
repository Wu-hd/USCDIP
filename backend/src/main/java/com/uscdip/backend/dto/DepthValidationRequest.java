package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private BigDecimal zTop;

    @JsonProperty("z_bottom")
    @JsonAlias({"zBottom", "zbottom"})
    private BigDecimal zBottom;

    @JsonProperty("bury_depth")
    @JsonAlias({"buryDepth", "burydepth"})
    private BigDecimal buryDepth;

    @JsonProperty("elevation_ref")
    @JsonAlias({"elevationRef", "elevationref"})
    private String elevationRef;

    @JsonProperty("tolerance")
    private BigDecimal tolerance;
}
