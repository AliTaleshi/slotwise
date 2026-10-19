package com.slotwise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.support.AbstractIntegrationTest;
import com.slotwise.support.MutableClock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AuthIntegrationTest extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired MutableClock clock;

  JsonNode register(String role) throws Exception {
    String email = "u-" + UUID.randomUUID() + "@example.com";
    String body =
        mvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"password123","name":"Test","role":"%s"}"""
                            .formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  @Test
  void registerLoginAndCallProtectedEndpoint() throws Exception {
    JsonNode reg = register("OWNER");
    String email = reg.at("/user/email").asText();

    String loginBody =
        mvc.perform(
                post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String access = json.readTree(loginBody).get("accessToken").asText();

    mvc.perform(get("/v1/_probe/any").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
  }

  @Test
  void duplicateEmailIsConflict() throws Exception {
    JsonNode reg = register("CUSTOMER");
    mvc.perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"password123","name":"Dup","role":"CUSTOMER"}"""
                        .formatted(reg.at("/user/email").asText())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void staffSelfRegistrationRejected() throws Exception {
    mvc.perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"s@example.com","password":"password123","name":"S","role":"STAFF"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void wrongPasswordIsUnauthorized() throws Exception {
    JsonNode reg = register("CUSTOMER");
    mvc.perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + reg.at("/user/email").asText()
                        + "\",\"password\":\"nope-nope\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void weakPasswordRejected() throws Exception {
    mvc.perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"w@example.com\",\"password\":\"short\",\"name\":\"W\",\"role\":\"OWNER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  void refreshRotatesAndOldTokenCannotBeReused() throws Exception {
    JsonNode reg = register("CUSTOMER");
    String first = reg.get("refreshToken").asText();

    String second = refresh(first, 200).get("refreshToken").asText();
    assertThat(second).isNotEqualTo(first);

    // reuse of the rotated token is treated as theft: the whole family is revoked
    refresh(first, 401);
    refresh(second, 401);
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    JsonNode reg = register("CUSTOMER");
    String token = reg.get("refreshToken").asText();
    mvc.perform(
            post("/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}"))
        .andExpect(status().isNoContent());
    refresh(token, 401);
  }

  @Test
  void expiredRefreshTokenRejected() throws Exception {
    JsonNode reg = register("CUSTOMER");
    clock.advance(Duration.ofDays(15));
    try {
      refresh(reg.get("refreshToken").asText(), 401);
    } finally {
      clock.advance(Duration.ofDays(-15));
    }
  }

  @Test
  void anonymousGetsProblemJson401() throws Exception {
    mvc.perform(get("/v1/_probe/any"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void garbageBearerIsUnauthorized() throws Exception {
    mvc.perform(get("/v1/_probe/any").header("Authorization", "Bearer nonsense"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rolesAreEnforced() throws Exception {
    String owner = register("OWNER").get("accessToken").asText();
    String customer = register("CUSTOMER").get("accessToken").asText();

    mvc.perform(get("/v1/_probe/owner").header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk());
    mvc.perform(get("/v1/_probe/owner").header("Authorization", "Bearer " + customer))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mvc.perform(get("/v1/_probe/staff").header("Authorization", "Bearer " + customer))
        .andExpect(status().isForbidden());
    mvc.perform(get("/v1/_probe/staff").header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk());
  }

  JsonNode refresh(String token, int expectedStatus) throws Exception {
    String body =
        mvc.perform(
                post("/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + token + "\"}"))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }
}
