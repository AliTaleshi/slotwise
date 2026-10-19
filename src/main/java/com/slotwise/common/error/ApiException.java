package com.slotwise.common.error;

/** Business-rule failure that maps to a Problem Details response with a stable code. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }

  public static ApiException notFound(String what) {
    return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.ACCESS_DENIED, message);
  }
}
