package com.project.resource_reservation_engine.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI resourceReservationEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resource Reservation Engine API")
                        .description("A domain-agnostic reservation system for booking limited-capacity resources, with optimistic locking, idempotency keys, and race-free waitlist promotion.")
                        .version("v1"))
                        .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}