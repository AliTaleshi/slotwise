package com.slotwise.auth.controller;

import com.slotwise.auth.dto.AuthDtos.LoginRequest;
import com.slotwise.auth.dto.AuthDtos.RefreshRequest;
import com.slotwise.auth.dto.AuthDtos.RegisterRequest;
import com.slotwise.auth.dto.AuthDtos.TokenResponse;
import com.slotwise.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @Operation(summary = "Register an owner or customer account")
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
    return auth.register(req);
  }

  @Operation(summary = "Log in with email and password")
  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest req) {
    return auth.login(req);
  }

  @Operation(summary = "Exchange a refresh token for a new token pair (rotation)")
  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
    return auth.refresh(req.refreshToken());
  }

  @Operation(summary = "Revoke a refresh token")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest req) {
    auth.logout(req.refreshToken());
  }
}
