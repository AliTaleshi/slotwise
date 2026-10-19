package com.slotwise.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Small helpers shared by integration tests. */
public class ApiTestSupport {

  private final MockMvc mvc;
  private final ObjectMapper json;

  public ApiTestSupport(MockMvc mvc, ObjectMapper json) {
    this.mvc = mvc;
    this.json = json;
  }

  /** Registers a fresh user and returns the access token. */
  public String register(String role) throws Exception {
    return registerFull(role).get("accessToken").asText();
  }

  public JsonNode registerFull(String role) throws Exception {
    String email = "u-" + UUID.randomUUID() + "@example.com";
    String body =
        mvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"%s\",\"password\":\"password123\",\"name\":\"Test\",\"role\":\"%s\"}"
                            .formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  public String login(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("accessToken").asText();
  }

  public JsonNode send(
      MockHttpServletRequestBuilder request, String token, Object body, int expected)
      throws Exception {
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      request
          .contentType(MediaType.APPLICATION_JSON)
          .content(body instanceof String s ? s : json.writeValueAsString(body));
    }
    String content =
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return content.isBlank() ? null : json.readTree(content);
  }
}
