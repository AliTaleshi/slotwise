package com.slotwise.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotwise.mail")
public record NotificationProperties(String from) {

  public NotificationProperties {
    from = from == null || from.isBlank() ? "Slotwise <no-reply@slotwise.local>" : from;
  }
}
