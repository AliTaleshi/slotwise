package com.slotwise.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Renders every failure as RFC 7807 Problem Details carrying a stable {@code code}. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  public static ProblemDetail problem(ErrorCode code, String detail) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
    pd.setTitle(code.name());
    pd.setProperty("code", code.name());
    return pd;
  }

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApi(ApiException ex) {
    return problem(ex.code(), ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(ErrorCode.ACCESS_DENIED, "You do not have access to this resource");
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraint(ConstraintViolationException ex) {
    ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, "Request validation failed");
    pd.setProperty(
        "errors",
        ex.getConstraintViolations().stream()
            .map(v -> new FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
            .toList());
    return pd;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    return problem(
        ErrorCode.CONCURRENT_MODIFICATION,
        "The resource was modified by someone else; reload and retry");
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return problem(ErrorCode.INTERNAL_ERROR, "Unexpected server error");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, "Request validation failed");
    List<FieldIssue> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldIssue(f.getField(), f.getDefaultMessage()))
            .toList();
    pd.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (response != null && response.getBody() instanceof ProblemDetail pd) {
      if (pd.getProperties() == null || !pd.getProperties().containsKey("code")) {
        ErrorCode code =
            status.value() == 404
                ? ErrorCode.NOT_FOUND
                : status.is4xxClientError()
                    ? ErrorCode.MALFORMED_REQUEST
                    : ErrorCode.INTERNAL_ERROR;
        pd.setProperty("code", code.name());
      }
    }
    return response;
  }

  public record FieldIssue(String field, String message) {}
}
