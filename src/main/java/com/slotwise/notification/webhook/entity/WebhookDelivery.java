package com.slotwise.notification.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

  public enum Status {
    PENDING,
    SUCCESS,
    FAILED
  }

  @Id private UUID id;

  @Column(name = "endpoint_id", nullable = false)
  private UUID endpointId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String payload;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  protected WebhookDelivery() {}

  public WebhookDelivery(UUID id, UUID endpointId, String eventType, String payload, Instant now) {
    this.id = id;
    this.endpointId = endpointId;
    this.eventType = eventType;
    this.payload = payload;
    this.nextAttemptAt = now;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEndpointId() {
    return endpointId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }
}
