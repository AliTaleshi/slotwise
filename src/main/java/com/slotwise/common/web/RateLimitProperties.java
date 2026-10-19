package com.slotwise.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotwise.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer publicPerMinute, Integer authPerMinute) {

  public RateLimitProperties {
    enabled = enabled == null || enabled;
    publicPerMinute = publicPerMinute == null ? 60 : publicPerMinute;
    authPerMinute = authPerMinute == null ? 10 : authPerMinute;
  }
}
