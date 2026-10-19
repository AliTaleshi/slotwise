package com.slotwise.auth.dto;

import com.slotwise.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @Schema(example = "owner@example.com") @NotBlank @Email @Size(max = 254) String email,
      @Schema(example = "correct-horse-battery") @NotBlank @Size(min = 8, max = 72) String password,
      @Schema(example = "Dana Owner") @NotBlank @Size(max = 120) String name,
      @Schema(description = "OWNER or CUSTOMER; staff accounts are created by owners") @NotNull
          Role role) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record TokenResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresInSeconds,
      UserInfo user) {}

  public record UserInfo(UUID id, String email, String name, Role role) {}
}
