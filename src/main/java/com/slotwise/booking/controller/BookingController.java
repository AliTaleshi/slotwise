package com.slotwise.booking.controller;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.booking.dto.BookingDtos.BookingResponse;
import com.slotwise.booking.dto.BookingDtos.CreateBookingRequest;
import com.slotwise.booking.dto.BookingDtos.RescheduleRequest;
import com.slotwise.booking.dto.BookingDtos.StatusUpdateRequest;
import com.slotwise.booking.service.BookingAccess.Caller;
import com.slotwise.booking.service.BookingService;
import com.slotwise.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Booking endpoints. Everything except the status update is reachable without an account: access is
 * decided in the service from either the bearer token or the signed manage-link token (sent as
 * {@code X-Manage-Token} header or {@code ?token=}).
 */
@RestController
@RequestMapping("/v1/bookings")
@Tag(name = "Bookings")
public class BookingController {

  private static final String MANAGE_HEADER = "X-Manage-Token";

  private final BookingService service;

  public BookingController(BookingService service) {
    this.service = service;
  }

  @Operation(
      summary = "Confirm a booking from a hold",
      description =
          "Guests get a manageToken/manageUrl in the response. Send an Idempotency-Key header so a"
              + " retried request returns the first booking instead of failing.")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookingResponse create(
      @Valid @RequestBody CreateBookingRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal AuthUser user) {
    return service.confirm(req, idempotencyKey, user);
  }

  @Operation(summary = "Booking details (customer, owner, staff or manage-link token)")
  @GetMapping("/{id}")
  public BookingResponse get(
      @PathVariable UUID id,
      @RequestHeader(value = MANAGE_HEADER, required = false) String manageHeader,
      @Parameter(description = "Manage-link token") @RequestParam(required = false) String token,
      @AuthenticationPrincipal AuthUser user) {
    return service.get(id, caller(user, manageHeader, token));
  }

  @Operation(summary = "Move a booking to another slot (atomic)")
  @PostMapping("/{id}/reschedule")
  public BookingResponse reschedule(
      @PathVariable UUID id,
      @Valid @RequestBody RescheduleRequest req,
      @RequestHeader(value = MANAGE_HEADER, required = false) String manageHeader,
      @RequestParam(required = false) String token,
      @AuthenticationPrincipal AuthUser user) {
    return service.reschedule(id, req, caller(user, manageHeader, token));
  }

  @Operation(
      summary = "Cancel a booking",
      description = "Customers and guests are bound by the business cutoff; owners are not.")
  @PostMapping("/{id}/cancel")
  public BookingResponse cancel(
      @PathVariable UUID id,
      @RequestHeader(value = MANAGE_HEADER, required = false) String manageHeader,
      @RequestParam(required = false) String token,
      @AuthenticationPrincipal AuthUser user) {
    return service.cancel(id, caller(user, manageHeader, token));
  }

  @Operation(
      summary = "Mark a booking COMPLETED or NO_SHOW",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  public BookingResponse updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody StatusUpdateRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.updateStatus(id, req.status(), user);
  }

  private static Caller caller(AuthUser user, String header, String query) {
    return new Caller(user, header != null && !header.isBlank() ? header : query);
  }
}
