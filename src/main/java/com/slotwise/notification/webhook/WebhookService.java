package com.slotwise.notification.webhook;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.notification.webhook.dto.WebhookDtos.AttemptResponse;
import com.slotwise.notification.webhook.dto.WebhookDtos.CreateWebhookRequest;
import com.slotwise.notification.webhook.dto.WebhookDtos.DeliveryResponse;
import com.slotwise.notification.webhook.dto.WebhookDtos.WebhookResponse;
import com.slotwise.notification.webhook.entity.WebhookEndpoint;
import com.slotwise.notification.webhook.repository.WebhookAttemptRepository;
import com.slotwise.notification.webhook.repository.WebhookDeliveryRepository;
import com.slotwise.notification.webhook.repository.WebhookEndpointRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final WebhookEndpointRepository endpoints;
  private final WebhookDeliveryRepository deliveries;
  private final WebhookAttemptRepository attempts;
  private final BusinessAccess access;
  private final WebhookProperties props;
  private final Clock clock;

  public WebhookService(
      WebhookEndpointRepository endpoints,
      WebhookDeliveryRepository deliveries,
      WebhookAttemptRepository attempts,
      BusinessAccess access,
      WebhookProperties props,
      Clock clock) {
    this.endpoints = endpoints;
    this.deliveries = deliveries;
    this.attempts = attempts;
    this.access = access;
    this.props = props;
    this.clock = clock;
  }

  @Transactional
  public WebhookResponse create(UUID businessId, CreateWebhookRequest req, AuthUser owner) {
    access.requireOwned(businessId, owner);
    String problem = UrlGuard.check(req.url(), props.allowPrivateTargets());
    if (problem != null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "url " + problem);
    }
    Set<WebhookEvent> events = parseEvents(req.events());
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String secret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    WebhookEndpoint saved =
        endpoints.save(new WebhookEndpoint(businessId, req.url(), secret, events, clock.instant()));
    return view(saved, secret);
  }

  @Transactional(readOnly = true)
  public List<WebhookResponse> list(UUID businessId, AuthUser owner) {
    access.requireOwned(businessId, owner);
    return endpoints.findByBusinessIdAndActiveTrueOrderByCreatedAtAsc(businessId).stream()
        .map(e -> view(e, null))
        .toList();
  }

  /** Deactivates the endpoint; its delivery log is kept. */
  @Transactional
  public void delete(UUID businessId, UUID endpointId, AuthUser owner) {
    load(businessId, endpointId, owner).deactivate();
  }

  @Transactional(readOnly = true)
  public List<DeliveryResponse> deliveries(
      UUID businessId, UUID endpointId, int limit, AuthUser owner) {
    load(businessId, endpointId, owner);
    return deliveries
        .findByEndpointIdOrderByCreatedAtDesc(
            endpointId, Pageable.ofSize(Math.max(1, Math.min(limit, 200))))
        .stream()
        .map(
            d ->
                new DeliveryResponse(
                    d.getId(),
                    d.getEventType(),
                    d.getStatus(),
                    d.getAttempts(),
                    d.getNextAttemptAt(),
                    d.getCreatedAt(),
                    d.getDeliveredAt(),
                    attempts.findByDeliveryIdOrderByAttemptedAtAsc(d.getId()).stream()
                        .map(
                            a ->
                                new AttemptResponse(
                                    a.getAttemptedAt(),
                                    a.getStatusCode(),
                                    a.getError(),
                                    a.getDurationMs()))
                        .toList()))
        .toList();
  }

  private WebhookEndpoint load(UUID businessId, UUID endpointId, AuthUser owner) {
    access.requireOwned(businessId, owner);
    return endpoints
        .findById(endpointId)
        .filter(e -> e.getBusinessId().equals(businessId))
        .orElseThrow(() -> ApiException.notFound("Webhook"));
  }

  private static Set<WebhookEvent> parseEvents(Set<String> requested) {
    if (requested == null || requested.isEmpty()) {
      return EnumSet.allOf(WebhookEvent.class);
    }
    try {
      return requested.stream()
          .map(WebhookEvent::fromType)
          .collect(Collectors.toCollection(() -> EnumSet.noneOf(WebhookEvent.class)));
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Unknown event. Supported: "
              + Arrays.stream(WebhookEvent.values()).map(WebhookEvent::type).toList());
    }
  }

  private static WebhookResponse view(WebhookEndpoint e, String secret) {
    return new WebhookResponse(
        e.getId(), e.getUrl(), e.eventTypes(), e.isActive(), e.getCreatedAt(), secret);
  }
}
