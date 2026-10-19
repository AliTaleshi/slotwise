package com.slotwise.notification.webhook;

import com.slotwise.notification.webhook.entity.WebhookAttempt;
import com.slotwise.notification.webhook.entity.WebhookDelivery;
import com.slotwise.notification.webhook.entity.WebhookDelivery.Status;
import com.slotwise.notification.webhook.entity.WebhookEndpoint;
import com.slotwise.notification.webhook.repository.WebhookAttemptRepository;
import com.slotwise.notification.webhook.repository.WebhookDeliveryRepository;
import com.slotwise.notification.webhook.repository.WebhookEndpointRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Performs one delivery attempt and records its outcome and the next retry time. */
@Component
public class WebhookDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

  private final WebhookDeliveryRepository deliveries;
  private final WebhookEndpointRepository endpoints;
  private final WebhookAttemptRepository attempts;
  private final WebhookProperties props;
  private final Clock clock;
  private final HttpClient http;

  public WebhookDispatcher(
      WebhookDeliveryRepository deliveries,
      WebhookEndpointRepository endpoints,
      WebhookAttemptRepository attempts,
      WebhookProperties props,
      Clock clock) {
    this.deliveries = deliveries;
    this.endpoints = endpoints;
    this.attempts = attempts;
    this.props = props;
    this.clock = clock;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(props.timeout())
            .followRedirects(HttpClient.Redirect.NEVER) // a redirect could hop to an internal host
            .build();
  }

  /** Attempts delivery unless someone else holds the lease or it is no longer due. */
  public void deliver(UUID deliveryId) {
    Instant now = clock.instant();
    if (deliveries.lease(deliveryId, now, now.plus(props.lease())) == 0) {
      return;
    }
    WebhookDelivery delivery = deliveries.findById(deliveryId).orElseThrow();
    WebhookEndpoint endpoint = endpoints.findById(delivery.getEndpointId()).orElseThrow();

    Integer statusCode = null;
    String error = null;
    long started = System.nanoTime();
    if (!endpoint.isActive()) {
      error = "endpoint deactivated";
    } else {
      String blocked = UrlGuard.check(endpoint.getUrl(), props.allowPrivateTargets());
      if (blocked != null) {
        error = "blocked target: " + blocked;
      } else {
        try {
          statusCode = post(endpoint, delivery, now).statusCode();
          if (statusCode < 200 || statusCode >= 300) {
            error = "HTTP " + statusCode;
          }
        } catch (Exception e) {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
      }
    }
    long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
    attempts.save(new WebhookAttempt(deliveryId, now, statusCode, error, ms));

    int made = delivery.getAttempts() + 1;
    if (error == null) {
      deliveries.finishAttempt(deliveryId, Status.SUCCESS, made, now, now);
      return;
    }
    Duration wait = !endpoint.isActive() ? null : RetryPolicy.delayAfter(made);
    if (wait == null) {
      deliveries.finishAttempt(deliveryId, Status.FAILED, made, now, null);
      log.warn("Webhook delivery {} gave up after {} attempts: {}", deliveryId, made, error);
    } else {
      deliveries.finishAttempt(deliveryId, Status.PENDING, made, now.plus(wait), null);
    }
  }

  private HttpResponse<Void> post(WebhookEndpoint endpoint, WebhookDelivery delivery, Instant now)
      throws Exception {
    String body = delivery.getPayload();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(endpoint.getUrl()))
            .timeout(props.timeout())
            .header("Content-Type", "application/json")
            .header("User-Agent", "Slotwise-Webhooks/1")
            .header("X-Slotwise-Event", delivery.getEventType())
            .header("X-Slotwise-Delivery", delivery.getId().toString())
            .header(
                "X-Slotwise-Signature",
                WebhookSigner.header(endpoint.getSecret(), now.getEpochSecond(), body))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.discarding());
  }
}
