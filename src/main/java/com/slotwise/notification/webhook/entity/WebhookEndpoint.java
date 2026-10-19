package com.slotwise.notification.webhook.entity;

import com.slotwise.notification.webhook.WebhookEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

  @Id private UUID id;

  @Column(name = "business_id", nullable = false)
  private UUID businessId;

  @Column(nullable = false)
  private String url;

  @Column(nullable = false)
  private String secret;

  @Column(nullable = false)
  private String events;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected WebhookEndpoint() {}

  public WebhookEndpoint(
      UUID businessId, String url, String secret, Set<WebhookEvent> events, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.businessId = businessId;
    this.url = url;
    this.secret = secret;
    this.events = events.stream().map(WebhookEvent::type).sorted().collect(Collectors.joining(","));
    this.createdAt = createdAt;
  }

  public boolean subscribesTo(WebhookEvent event) {
    return Arrays.asList(events.split(",")).contains(event.type());
  }

  public Set<String> eventTypes() {
    return Set.of(events.split(","));
  }

  public UUID getId() {
    return id;
  }

  public UUID getBusinessId() {
    return businessId;
  }

  public String getUrl() {
    return url;
  }

  public String getSecret() {
    return secret;
  }

  public boolean isActive() {
    return active;
  }

  public void deactivate() {
    this.active = false;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
