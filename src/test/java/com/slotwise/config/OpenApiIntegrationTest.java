package com.slotwise.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void specIsPublicAndDocumentsEndpointsAndErrors() throws Exception {
    String body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
    JsonNode spec = json.readTree(body);
    assertThat(spec.at("/info/title").asText()).isEqualTo("Slotwise API");
    assertThat(spec.at("/paths").has("/v1/holds")).isTrue();
    assertThat(spec.at("/paths").has("/v1/bookings/{id}/reschedule")).isTrue();
    assertThat(spec.at("/paths").has("/v1/businesses/{slug}/availability")).isTrue();
    assertThat(spec.at("/components/schemas/Problem/properties/code").isMissingNode()).isFalse();
    assertThat(
            spec.at("/paths/~1v1~1holds/post/responses/409/content")
                    .has("application~1problem+json")
                || spec.at("/paths/~1v1~1holds/post/responses/409/content")
                    .has("application/problem+json"))
        .isTrue();
    // every v1 operation has a summary
    spec.at("/paths")
        .fields()
        .forEachRemaining(
            p -> {
              if (p.getKey().startsWith("/v1/") && !p.getKey().startsWith("/v1/_probe")) {
                p.getValue()
                    .fields()
                    .forEachRemaining(
                        op ->
                            assertThat(op.getValue().has("summary"))
                                .as(p.getKey() + " " + op.getKey())
                                .isTrue());
              }
            });
  }

  @Test
  void swaggerUiIsReachable() throws Exception {
    int status = mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus();
    assertThat(status).isEqualTo(200);
  }
}
