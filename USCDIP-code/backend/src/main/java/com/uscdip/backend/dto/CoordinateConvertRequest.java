package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoordinateConvertRequest {

    @JsonProperty("authority_srid")
    @JsonAlias({"authoritySrid", "authoritysrid"})
    @Schema(description = "权威坐标系", example = "EPSG:4490")
    @NotBlank(message = "authoritySrid is required")
    private String authoritySrid;

    @JsonProperty("display_srid")
    @JsonAlias({"displaySrid", "displaysrid"})
    @Schema(description = "显示坐标系", example = "EPSG:3857")
    @NotBlank(message = "displaySrid is required")
    private String displaySrid;

    @JsonProperty("geometry_2d")
    @JsonAlias({"geometry2d", "geometry2D"})
    @Schema(description = "二维几何（WKT）", example = "POINT(120.1533 30.2741)")
    @NotBlank(message = "geometry2d is required")
    private String geometry2d;
}
