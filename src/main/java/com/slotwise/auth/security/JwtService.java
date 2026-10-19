package com.slotwise.auth.security;

import com.slotwise.auth.entity.Role;
import com.slotwise.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies short-lived HS256 access tokens. Time comes from the injected Clock. */
@Service
public class JwtService {

  private final JwtProperties props;
  private final Clock clock;
  private final SecretKey key;

  public JwtService(JwtProperties props, Clock clock) {
    this.props = props;
    this.clock = clock;
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes());
  }

  public String issueAccessToken(User user) {
    Instant now = clock.instant();
    return Jwts.builder()
        .issuer(props.issuer())
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(props.accessTtl())))
        .signWith(key)
        .compact();
  }

  public long accessTtlSeconds() {
    return props.accessTtl().toSeconds();
  }

  /** Returns the principal for a valid, unexpired token; empty otherwise. */
  public Optional<AuthUser> parse(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(key)
              .requireIssuer(props.issuer())
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      return Optional.of(
          new AuthUser(
              UUID.fromString(claims.getSubject()),
              claims.get("email", String.class),
              Role.valueOf(claims.get("role", String.class))));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
