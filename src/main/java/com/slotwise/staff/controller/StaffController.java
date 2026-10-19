package com.slotwise.staff.controller;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.config.OpenApiConfig;
import com.slotwise.staff.dto.StaffDtos.CreateStaffRequest;
import com.slotwise.staff.dto.StaffDtos.StaffResponse;
import com.slotwise.staff.dto.StaffDtos.UpdateStaffRequest;
import com.slotwise.staff.service.StaffService;
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
@RequestMapping("/v1/businesses/{businessId}/staff")
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Staff")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffController {

  private final StaffService service;

  public StaffController(StaffService service) {
    this.service = service;
  }

  @Operation(summary = "Add a staff member, optionally with a STAFF login")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StaffResponse create(
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateStaffRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.create(businessId, req, user);
  }

  @Operation(summary = "List staff, including inactive")
  @GetMapping
  public List<StaffResponse> list(
      @PathVariable UUID businessId, @AuthenticationPrincipal AuthUser user) {
    return service.list(businessId, user);
  }

  @Operation(summary = "Update a staff member or reassign services")
  @PatchMapping("/{id}")
  public StaffResponse update(
      @PathVariable UUID businessId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateStaffRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.update(businessId, id, req, user);
  }

  @Operation(summary = "Deactivate a staff member (existing bookings are kept)")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID businessId,
      @PathVariable UUID id,
      @AuthenticationPrincipal AuthUser user) {
    service.deactivate(businessId, id, user);
  }
}
