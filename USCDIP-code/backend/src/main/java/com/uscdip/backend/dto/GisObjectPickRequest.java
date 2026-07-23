package com.uscdip.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record GisObjectPickRequest(
        @NotNull @JsonProperty("x") BigDecimal x,
        @NotNull @JsonProperty("y") BigDecimal y,
        @NotBlank
        @JsonProperty("authority_srid")
        @JsonAlias({"authoritySrid"})
        String authoritySrid,
        @NotBlank
        @JsonProperty("display_srid")
        @JsonAlias({"displaySrid"})
        String displaySrid,
        @JsonProperty("object_types")
        @JsonAlias({"objectTypes"})
        List<String> objectTypes,
        @DecimalMin(value = "0.0")
        @JsonProperty("tolerance_meters")
        @JsonAlias({"toleranceMeters"})
        BigDecimal toleranceMeters
) {
}
