package com.slotwise.notification.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_attempts")
public class WebhookAttempt {

  @Id private UUID id;

  @Column(name = "delivery_id", nullable = false)
  private UUID deliveryId;

  @Column(name = "attempted_at", nullable = false)
  private Instant attemptedAt;

  @Column(name = "status_code")
  private Integer statusCode;

  private String error;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  protected WebhookAttempt() {}

  public WebhookAttempt(
      UUID deliveryId, Instant attemptedAt, Integer statusCode, String error, long durationMs) {
    this.id = UUID.randomUUID();
    this.deliveryId = deliveryId;
    this.attemptedAt = attemptedAt;
    this.statusCode = statusCode;
    this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    this.durationMs = durationMs;
  }

  public UUID getDeliveryId() {
    return deliveryId;
  }

  public Instant getAttemptedAt() {
    return attemptedAt;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public String getError() {
    return error;
  }

  public long getDurationMs() {
    return durationMs;
  }
}
