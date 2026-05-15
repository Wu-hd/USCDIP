package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoordinateConvertRequest {

    @JsonProperty("authority_srid")
    @JsonAlias({"authoritySrid", "authoritysrid"})
    @NotBlank(message = "authority_srid is required")
    private String authoritySrid;

    @JsonProperty("display_srid")
    @JsonAlias({"displaySrid", "displaysrid"})
    @NotBlank(message = "display_srid is required")
    private String displaySrid;

    @JsonProperty("geometry_2d")
    @JsonAlias({"geometry2d", "geometry2D"})
    @NotBlank(message = "geometry_2d is required")
    private String geometry2d;
}
