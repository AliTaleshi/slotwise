package com.slotwise.notification.webhook.dto;

import com.slotwise.notification.webhook.entity.WebhookDelivery.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WebhookDtos {

  private WebhookDtos() {}

  public record CreateWebhookRequest(
      @Schema(example = "https://example.com/hooks/slotwise") @NotBlank @Size(max = 500) String url,
      @Schema(
              description = "Defaults to all events",
              example = "[\"booking.created\", \"booking.cancelled\"]")
          Set<String> events) {}

  public record WebhookResponse(
      UUID id,
      String url,
      Set<String> events,
      boolean active,
      Instant createdAt,
      @Schema(description = "Signing secret; shown only once, when the webhook is created")
          String secret) {}

  public record AttemptResponse(
      Instant attemptedAt, Integer statusCode, String error, long durationMs) {}

  public record DeliveryResponse(
      UUID id,
      String eventType,
      Status status,
      int attempts,
      Instant nextAttemptAt,
      Instant createdAt,
      Instant deliveredAt,
      List<AttemptResponse> attemptLog) {}
}
