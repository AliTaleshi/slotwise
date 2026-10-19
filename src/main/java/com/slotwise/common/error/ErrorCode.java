package com.slotwise.common.error;

import org.springframework.http.HttpStatus;

/** Stable, client-facing error codes. Never rename a code once released. */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
  ACCESS_DENIED(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  CONFLICT(HttpStatus.CONFLICT),
  EMAIL_TAKEN(HttpStatus.CONFLICT),
  SLUG_TAKEN(HttpStatus.CONFLICT),
  SLOT_UNAVAILABLE(HttpStatus.CONFLICT),
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
  IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT),
  HOLD_EXPIRED(HttpStatus.GONE),
  CANCELLATION_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY),
  INVALID_STATE(HttpStatus.UNPROCESSABLE_ENTITY),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
