package com.slotwise.notification.webhook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slotwise.webhooks")
public record WebhookProperties(Boolean allowPrivateTargets, Duration timeout, Duration lease) {

  public WebhookProperties {
    allowPrivateTargets = allowPrivateTargets != null && allowPrivateTargets;
    timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    lease = lease == null ? Duration.ofMinutes(2) : lease;
  }
}
