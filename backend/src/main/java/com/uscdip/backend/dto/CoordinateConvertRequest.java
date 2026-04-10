package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoordinateConvertRequest {

    @JsonProperty("authority_srid")
    @JsonAlias({"authoritySrid", "authoritysrid"})
    private String authoritySrid;

    @JsonProperty("display_srid")
    @JsonAlias({"displaySrid", "displaysrid"})
    private String displaySrid;

    @JsonProperty("geometry_2d")
    @JsonAlias({"geometry2d", "geometry2D"})
    private String geometry2d;
}
