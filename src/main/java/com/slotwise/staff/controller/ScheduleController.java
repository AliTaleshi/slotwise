package com.slotwise.staff.controller;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.config.OpenApiConfig;
import com.slotwise.staff.dto.StaffDtos.CreateTimeOffRequest;
import com.slotwise.staff.dto.StaffDtos.SetWorkingHoursRequest;
import com.slotwise.staff.dto.StaffDtos.TimeOffResponse;
import com.slotwise.staff.dto.StaffDtos.WorkingHoursResponse;
import com.slotwise.staff.service.ScheduleService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/staff/{staffId}")
@Tag(name = "Staff schedule")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ScheduleController {

  private final ScheduleService service;

  public ScheduleController(ScheduleService service) {
    this.service = service;
  }

  @Operation(summary = "Replace the weekly working hours (owner only)")
  @PutMapping("/working-hours")
  @PreAuthorize("hasRole('OWNER')")
  public WorkingHoursResponse setWorkingHours(
      @PathVariable UUID staffId,
      @Valid @RequestBody SetWorkingHoursRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.setWorkingHours(staffId, req, user);
  }

  @Operation(summary = "Read the weekly working hours")
  @GetMapping("/working-hours")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  public WorkingHoursResponse getWorkingHours(
      @PathVariable UUID staffId, @AuthenticationPrincipal AuthUser user) {
    return service.getWorkingHours(staffId, user);
  }

  @Operation(summary = "Add time off, a closure, or an extra working window")
  @PostMapping("/time-off")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  @ResponseStatus(HttpStatus.CREATED)
  public TimeOffResponse addTimeOff(
      @PathVariable UUID staffId,
      @Valid @RequestBody CreateTimeOffRequest req,
      @AuthenticationPrincipal AuthUser user) {
    return service.addTimeOff(staffId, req, user);
  }

  @Operation(summary = "List time off entries")
  @GetMapping("/time-off")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  public List<TimeOffResponse> listTimeOff(
      @PathVariable UUID staffId, @AuthenticationPrincipal AuthUser user) {
    return service.listTimeOff(staffId, user);
  }

  @Operation(summary = "Delete a time off entry")
  @DeleteMapping("/time-off/{timeOffId}")
  @PreAuthorize("hasAnyRole('OWNER','STAFF')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTimeOff(
      @PathVariable UUID staffId,
      @PathVariable UUID timeOffId,
      @AuthenticationPrincipal AuthUser user) {
    service.deleteTimeOff(staffId, timeOffId, user);
  }
}
