package com.slotwise.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

  /** Documents the shared error format on every /v1 operation. */
  @Bean
  public OpenApiCustomizer problemResponses() {
    return openApi -> {
      Schema<?> problem =
          new ObjectSchema()
              .addProperty("type", new StringSchema().example("about:blank"))
              .addProperty("title", new StringSchema().example("SLOT_UNAVAILABLE"))
              .addProperty("status", new Schema<Integer>().type("integer").example(409))
              .addProperty(
                  "detail", new StringSchema().example("This time slot is no longer available"))
              .addProperty(
                  "code",
                  new StringSchema()
                      .description("Stable machine-readable error code")
                      .example("SLOT_UNAVAILABLE"))
              .description("RFC 7807 Problem Details");
      openApi.getComponents().addSchemas("Problem", problem);
      String[][] errors = {
        {"400", "Validation failed (VALIDATION_FAILED, MALFORMED_REQUEST)"},
        {"401", "Missing or invalid credentials (UNAUTHENTICATED, INVALID_TOKEN)"},
        {"403", "Not allowed for this caller (ACCESS_DENIED)"},
        {"404", "Not found (NOT_FOUND)"},
        {"409", "Conflict, e.g. SLOT_UNAVAILABLE, EMAIL_TAKEN, CONCURRENT_MODIFICATION"},
        {"429", "Rate limited (RATE_LIMITED); see Retry-After"}
      };
      openApi.getPaths().entrySet().stream()
          .filter(e -> e.getKey().startsWith("/v1/"))
          .forEach(
              e ->
                  e.getValue()
                      .readOperations()
                      .forEach(
                          op -> {
                            for (String[] err : errors) {
                              op.getResponses()
                                  .addApiResponse(
                                      err[0],
                                      new ApiResponse()
                                          .description(err[1])
                                          .content(
                                              new Content()
                                                  .addMediaType(
                                                      "application/problem+json",
                                                      new MediaType()
                                                          .schema(
                                                              new Schema<>()
                                                                  .$ref(
                                                                      "#/components/schemas/Problem")))));
                            }
                          }));
    };
  }
}
