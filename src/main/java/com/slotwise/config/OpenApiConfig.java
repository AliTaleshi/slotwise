package com.slotwise.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  public static final String BEARER = "bearerAuth";

  @Bean
  public OpenAPI slotwiseOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Slotwise API")
                .version("v1")
                .description(
                    "Appointment booking backend: availability, holds, bookings, reminders and"
                        + " webhooks. Errors follow RFC 7807 with a stable `code` field."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
