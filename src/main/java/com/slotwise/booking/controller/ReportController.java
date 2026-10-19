package com.slotwise.booking.controller;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.booking.dto.BookingDtos.BookingResponse;
import com.slotwise.booking.dto.ReportDtos.CalendarLinkResponse;
import com.slotwise.booking.dto.ReportDtos.StatsResponse;
import com.slotwise.booking.entity.BookingStatus;
import com.slotwise.booking.service.BookingReportService;
import com.slotwise.common.web.PageResponse;
import com.slotwise.config.OpenApiConfig;
import com.slotwise.staff.service.CalendarFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Owner and staff tools")
public class ReportController {

  private final BookingReportService reports;
  private final CalendarFeedService calendar;

  public ReportController(BookingReportService reports, CalendarFeedService calendar) {
    this.reports = reports;
    this.calendar = calendar;
  }

  @Operation(
      summary = "List and filter bookings (paginated, by local start date)",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @GetMapping("/v1/businesses/{businessId}/bookings")
  @PreAuthorize("hasRole('OWNER')")
  public PageResponse<BookingResponse> list(
      @PathVariable UUID businessId,
      @RequestParam(required = false) UUID staffId,
      @RequestParam(required = false) BookingStatus status,
      @Parameter(example = "2030-01-01")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(example = "2030-01-31")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return reports.list(businessId, staffId, status, from, to, page, size, user);
  }

  @Operation(
      summary =
          "Booking statistics: per-day counts, cancellation and no-show rates, revenue estimate",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @GetMapping("/v1/businesses/{businessId}/stats")
  @PreAuthorize("hasRole('OWNER')")
  public StatsResponse stats(
      @PathVariable UUID businessId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AuthUser user) {
    return reports.stats(businessId, from, to, user);
  }

  @Operation(
      summary = "A staff member's schedule for a date range (own schedule, or any for owners)",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @GetMapping("/v1/staff/{staffId}/schedule")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  public List<BookingResponse> schedule(
      @PathVariable UUID staffId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal AuthUser user) {
    return reports.schedule(staffId, from, to, user);
  }

  @Operation(
      summary = "Get the private iCal subscription URL for a staff member",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER))
  @GetMapping("/v1/staff/{staffId}/calendar-link")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  public CalendarLinkResponse calendarLink(
      @PathVariable UUID staffId, @AuthenticationPrincipal AuthUser user) {
    return new CalendarLinkResponse(calendar.linkFor(staffId, user));
  }

  @Operation(
      summary = "iCal feed to subscribe to in Google or Apple Calendar",
      description = "Authenticated by the token in the URL; get the full URL from calendar-link.")
  @GetMapping(value = "/v1/staff/{staffId}/calendar.ics", produces = "text/calendar")
  public ResponseEntity<byte[]> feed(@PathVariable UUID staffId, @RequestParam String token) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
        .cacheControl(CacheControl.noStore())
        .body(calendar.feed(staffId, token).getBytes(StandardCharsets.UTF_8));
  }
}
