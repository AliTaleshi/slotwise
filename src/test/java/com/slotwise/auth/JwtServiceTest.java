package com.slotwise.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotwise.auth.entity.Role;
import com.slotwise.auth.entity.User;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.auth.security.JwtProperties;
import com.slotwise.auth.security.JwtService;
import com.slotwise.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  static final String SECRET = "unit-test-secret-unit-test-secret-1234";

  MutableClock clock;
  JwtService jwt;
  User user;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
    jwt =
        new JwtService(new JwtProperties(SECRET, "slotwise", Duration.ofMinutes(15), null), clock);
    user = new User("a@b.com", "x", "A", Role.OWNER, clock.instant());
  }

  @Test
  void roundTripsPrincipal() {
    AuthUser parsed = jwt.parse(jwt.issueAccessToken(user)).orElseThrow();
    assertThat(parsed.id()).isEqualTo(user.getId());
    assertThat(parsed.role()).isEqualTo(Role.OWNER);
    assertThat(parsed.email()).isEqualTo("a@b.com");
  }

  @Test
  void rejectsExpiredToken() {
    String token = jwt.issueAccessToken(user);
    clock.advance(Duration.ofMinutes(16));
    assertThat(jwt.parse(token)).isEmpty();
  }

  @Test
  void acceptsTokenJustBeforeExpiry() {
    String token = jwt.issueAccessToken(user);
    clock.advance(Duration.ofMinutes(14));
    assertThat(jwt.parse(token)).isPresent();
  }

  @Test
  void rejectsTamperedToken() {
    String token = jwt.issueAccessToken(user);
    assertThat(jwt.parse(token.substring(0, token.length() - 2) + "xx")).isEmpty();
  }

  @Test
  void rejectsTokenSignedWithOtherSecret() {
    JwtService other =
        new JwtService(
            new JwtProperties("another-secret-another-secret-12345678", "slotwise", null, null),
            clock);
    assertThat(jwt.parse(other.issueAccessToken(user))).isEmpty();
  }

  @Test
  void rejectsGarbage() {
    assertThat(jwt.parse("not-a-jwt")).isEmpty();
  }
}
