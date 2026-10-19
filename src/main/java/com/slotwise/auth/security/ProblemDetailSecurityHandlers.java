package com.slotwise.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Security-layer failures use the same Problem Details format as controller errors. */
@Component
public class ProblemDetailSecurityHandlers
    implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper mapper;

  public ProblemDetailSecurityHandlers(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    write(
        response,
        GlobalExceptionHandler.problem(ErrorCode.UNAUTHENTICATED, "Authentication required"));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    write(
        response,
        GlobalExceptionHandler.problem(
            ErrorCode.ACCESS_DENIED, "You do not have access to this resource"));
  }

  private void write(HttpServletResponse response, ProblemDetail pd) throws IOException {
    response.setStatus(pd.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), pd);
  }
}
