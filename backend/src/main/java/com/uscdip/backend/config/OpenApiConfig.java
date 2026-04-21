package com.uscdip.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI uscdipOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("USCDIP Backend API")
                        .version("v1")
                        .description("B-01 unified API contract draft with response envelope, pagination and error codes"));
    }
}
