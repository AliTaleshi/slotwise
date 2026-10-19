package com.slotwise.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.slotwise.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(
    properties = {
      "slotwise.rate-limit.public-per-minute=5",
      "slotwise.rate-limit.auth-per-minute=3"
    })
class RateLimitIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  void publicEndpointsAreLimitedWithRetryAfterAndProblemJson() throws Exception {
    String url =
        "/v1/businesses/nope/availability?serviceId="
            + UUID.randomUUID()
            + "&from=2030-01-01&to=2030-01-01";
    for (int i = 0; i < 5; i++) {
      assertThat(mvc.perform(get(url)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }
    var limited = mvc.perform(get(url)).andReturn().getResponse();
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isNotNull();
    assertThat(limited.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    // other public routes share the same per-client bucket
    assertThat(
            mvc.perform(post("/v1/holds").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(429);
  }

  @Test
  void loginAttemptsHaveTheirOwnStricterBucket() throws Exception {
    String body = "{\"email\":\"nobody@example.com\",\"password\":\"password123\"}";
    for (int i = 0; i < 3; i++) {
      assertThat(
              mvc.perform(
                      post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .isEqualTo(401);
    }
    assertThat(
            mvc.perform(
                    post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(429);
  }

  @Test
  void authenticatedOwnerEndpointsAreNotLimited() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus())
          .isEqualTo(200);
    }
  }
}
