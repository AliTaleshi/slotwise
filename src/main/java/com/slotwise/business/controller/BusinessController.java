package com.slotwise.business.controller;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.dto.BusinessDtos.BusinessResponse;
import com.slotwise.business.dto.BusinessDtos.CreateBusinessRequest;
import com.slotwise.business.dto.BusinessDtos.PublicBusinessResponse;
import com.slotwise.business.dto.BusinessDtos.UpdateBusinessRequest;
import com.slotwise.business.service.BusinessService;
import com.slotwise.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/businesses")
@Tag(name = "Businesses")
public class BusinessController {

  private final BusinessService service;

  public BusinessController(BusinessService service) {
    this.service = service;
  }

  @Operation(
      summary = "Create a business",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @PostMapping
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(HttpStatus.CREATED)
  public BusinessResponse create(
      @Valid @RequestBody CreateBusinessRequest req, @AuthenticationPrincipal AuthUser user) {
    return service.create(req, user);
  }

  @Operation(
      summary = "List my businesses",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public List<BusinessResponse> list(@AuthenticationPrincipal AuthUser user) {
    return service.listOwned(user);
  }

  @Operation(
      summary = "Get one of my businesses",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public BusinessResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
    return service.get(id, user);
  }

  @Operation(
      summary = "Update business details and booking policies",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('OWNER')")
  public BusinessResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBusinessRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.update(id, req, user);
  }

  @Operation(summary = "Public business profile with services and staff")
  @GetMapping("/{slug}/public")
  public PublicBusinessResponse publicProfile(@PathVariable String slug) {
    return service.publicProfile(slug);
  }
}
