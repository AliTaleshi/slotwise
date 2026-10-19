package com.slotwise.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.common.error.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  record Body(@NotBlank String name) {}

  @RestController
  static class Probe {
    @GetMapping("/boom")
    String boom() {
      throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "taken");
    }

    @GetMapping("/crash")
    String crash() {
      throw new IllegalStateException("secret internals");
    }

    @PostMapping("/validate")
    String validate(@Valid @RequestBody Body body) {
      return body.name();
    }
  }

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void apiExceptionBecomesProblemWithStableCode() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"))
        .andExpect(jsonPath("$.detail").value("taken"));
  }

  @Test
  void unexpectedErrorsDoNotLeakInternals() throws Exception {
    mvc.perform(get("/crash"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("Unexpected server error"));
  }

  @Test
  void validationErrorsListFields() throws Exception {
    mvc.perform(
            post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void malformedJsonGetsCode() throws Exception {
    mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }
}
