package com.slotwise.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotwise.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {

  public JwtProperties {
    if (secret == null || secret.getBytes().length < 32) {
      throw new IllegalStateException("slotwise.jwt.secret (JWT_SECRET) must be at least 32 bytes");
    }
    issuer = issuer == null ? "slotwise" : issuer;
    accessTtl = accessTtl == null ? Duration.ofMinutes(15) : accessTtl;
    refreshTtl = refreshTtl == null ? Duration.ofDays(14) : refreshTtl;
  }
}
