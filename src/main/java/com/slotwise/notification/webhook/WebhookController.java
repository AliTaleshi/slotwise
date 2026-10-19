package com.slotwise.notification.webhook;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.config.OpenApiConfig;
import com.slotwise.notification.webhook.dto.WebhookDtos.CreateWebhookRequest;
import com.slotwise.notification.webhook.dto.WebhookDtos.DeliveryResponse;
import com.slotwise.notification.webhook.dto.WebhookDtos.WebhookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/businesses/{businessId}/webhooks")
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Webhooks")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class WebhookController {

  private final WebhookService service;

  public WebhookController(WebhookService service) {
    this.service = service;
  }

  @Operation(
      summary = "Register a webhook endpoint",
      description =
          "Requests carry X-Slotwise-Signature: t=<unix>,v1=<hex HMAC-SHA256 of \"<t>.<body>\">."
              + " The secret is returned only here.")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WebhookResponse create(
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateWebhookRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.create(businessId, req, user);
  }

  @Operation(summary = "List active webhook endpoints")
  @GetMapping
  public List<WebhookResponse> list(
      @PathVariable UUID businessId, @AuthenticationPrincipal AuthUser user) {
    return service.list(businessId, user);
  }

  @Operation(summary = "Delete (deactivate) a webhook endpoint")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID businessId,
      @PathVariable UUID id,
      @AuthenticationPrincipal AuthUser user) {
    service.delete(businessId, id, user);
  }

  @Operation(summary = "Delivery log with every attempt, newest first")
  @GetMapping("/{id}/deliveries")
  public List<DeliveryResponse> deliveries(
      @PathVariable UUID businessId,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "50") int limit,
      @AuthenticationPrincipal AuthUser user) {
    return service.deliveries(businessId, id, limit, user);
  }
}
