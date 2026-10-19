package com.slotwise.offering.controller;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.config.OpenApiConfig;
import com.slotwise.offering.dto.OfferingDtos.CreateOfferingRequest;
import com.slotwise.offering.dto.OfferingDtos.OfferingResponse;
import com.slotwise.offering.dto.OfferingDtos.UpdateOfferingRequest;
import com.slotwise.offering.service.OfferingService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/businesses/{businessId}/services")
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Services")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class OfferingController {

  private final OfferingService service;

  public OfferingController(OfferingService service) {
    this.service = service;
  }

  @Operation(summary = "Add a service (duration, price, buffers)")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OfferingResponse create(
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateOfferingRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.create(businessId, req, user);
  }

  @Operation(summary = "List all services, including inactive ones")
  @GetMapping
  public List<OfferingResponse> list(
      @PathVariable UUID businessId, @AuthenticationPrincipal AuthUser user) {
    return service.list(businessId, user);
  }

  @Operation(summary = "Update a service")
  @PatchMapping("/{id}")
  public OfferingResponse update(
      @PathVariable UUID businessId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateOfferingRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.update(businessId, id, req, user);
  }

  @Operation(summary = "Deactivate a service (existing bookings are kept)")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID businessId,
      @PathVariable UUID id,
      @AuthenticationPrincipal AuthUser user) {
    service.deactivate(businessId, id, user);
  }
}
