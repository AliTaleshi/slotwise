package com.slotwise.booking.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotwise.booking")
public record BookingProperties(Duration holdTtl, String publicBaseUrl) {

  public BookingProperties {
    holdTtl = holdTtl == null ? Duration.ofMinutes(10) : holdTtl;
    publicBaseUrl = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl;
  }
}
