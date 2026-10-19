package com.slotwise.auth.service;

import com.slotwise.auth.dto.AuthDtos.LoginRequest;
import com.slotwise.auth.dto.AuthDtos.RegisterRequest;
import com.slotwise.auth.dto.AuthDtos.TokenResponse;
import com.slotwise.auth.dto.AuthDtos.UserInfo;
import com.slotwise.auth.entity.RefreshToken;
import com.slotwise.auth.entity.Role;
import com.slotwise.auth.entity.User;
import com.slotwise.auth.repository.RefreshTokenRepository;
import com.slotwise.auth.repository.UserRepository;
import com.slotwise.auth.security.JwtProperties;
import com.slotwise.auth.security.JwtService;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final JwtProperties props;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder encoder,
      JwtService jwt,
      JwtProperties props,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.encoder = encoder;
    this.jwt = jwt;
    this.props = props;
    this.clock = clock;
  }

  @Transactional
  public TokenResponse register(RegisterRequest req) {
    if (req.role() == Role.STAFF) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Staff accounts are created by a business owner");
    }
    String email = normalize(req.email());
    if (users.existsByEmail(email)) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
    }
    try {
      User user =
          users.saveAndFlush(
              new User(
                  email,
                  encoder.encode(req.password()),
                  req.name().trim(),
                  req.role(),
                  clock.instant()));
      return issue(user);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
    }
  }

  @Transactional
  public TokenResponse login(LoginRequest req) {
    User user =
        users
            .findByEmail(normalize(req.email()))
            .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
            .orElseThrow(
                () -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
    return issue(user);
  }

  /** Rotates the refresh token. Re-presenting a revoked token revokes the whole family. */
  @Transactional(noRollbackFor = ApiException.class)
  public TokenResponse refresh(String rawToken) {
    Instant now = clock.instant();
    RefreshToken stored =
        refreshTokens
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));
    if (stored.isRevoked()) {
      refreshTokens.revokeAllForUser(stored.getUserId(), now);
      throw new ApiException(ErrorCode.INVALID_TOKEN, "Refresh token was already used");
    }
    if (!stored.getExpiresAt().isAfter(now)) {
      throw new ApiException(ErrorCode.INVALID_TOKEN, "Refresh token expired");
    }
    stored.revoke(now);
    User user =
        users
            .findById(stored.getUserId())
            .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));
    return issue(user);
  }

  @Transactional
  public void logout(String rawToken) {
    refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
  }

  private TokenResponse issue(User user) {
    byte[] bytes = new byte[48];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    refreshTokens.save(
        new RefreshToken(user.getId(), hash(raw), clock.instant().plus(props.refreshTtl())));
    return new TokenResponse(
        jwt.issueAccessToken(user),
        raw,
        "Bearer",
        jwt.accessTtlSeconds(),
        new UserInfo(user.getId(), user.getEmail(), user.getName(), user.getRole()));
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase();
  }

  static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
