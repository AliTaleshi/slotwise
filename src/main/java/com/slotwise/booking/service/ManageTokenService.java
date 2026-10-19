package com.slotwise.booking.service;

import com.slotwise.auth.security.JwtProperties;
import com.slotwise.booking.entity.Booking;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Signed "manage link" tokens that let a guest view, reschedule or cancel one booking without an
 * account. Signed with a key derived from (not equal to) the JWT secret so an access token can
 * never be used as a manage token or vice versa.
 */
@Service
public class ManageTokenService {

  private static final String PURPOSE = "manage-booking";
  private static final Duration GRACE = Duration.ofDays(30);

  private final Clock clock;
  private final SecretKey key;

  public ManageTokenService(JwtProperties props, Clock clock) {
    this.clock = clock;
    this.key = Keys.hmacShaKeyFor(sha256("manage-link:" + props.secret()));
  }

  public String issue(Booking booking) {
    return Jwts.builder()
        .subject(booking.getId().toString())
        .claim("purpose", PURPOSE)
        .issuedAt(Date.from(clock.instant()))
        .expiration(Date.from(booking.getEndsAt().plus(GRACE)))
        .signWith(key)
        .compact();
  }

  /** True if the token is genuine, unexpired and issued for exactly this booking. */
  public boolean isValidFor(String token, UUID bookingId) {
    if (token == null || token.isBlank()) {
      return false;
    }
    try {
      var claims =
          Jwts.parser()
              .verifyWith(key)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      return PURPOSE.equals(claims.get("purpose", String.class))
          && bookingId.toString().equals(claims.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
