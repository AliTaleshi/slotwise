package com.slotwise.availability.controller;

import com.slotwise.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.slotwise.availability.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/businesses/{slug}/availability")
@Tag(name = "Availability")
public class AvailabilityController {

  private final AvailabilityService service;

  public AvailabilityController(AvailabilityService service) {
    this.service = service;
  }

  @Operation(
      summary = "Available start times for a service",
      description =
          "Slots are grouped by local date in the business time zone and carry UTC offsets. "
              + "Without staffId, a slot is listed if any qualified staff member is free.")
  @GetMapping
  public AvailabilityResponse availability(
      @PathVariable String slug,
      @RequestParam UUID serviceId,
      @Parameter(description = "Optional: restrict to one staff member")
          @RequestParam(required = false)
          UUID staffId,
      @Parameter(description = "First local date, inclusive", example = "2030-03-25")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(
              description = "Last local date, inclusive (max 62 days range)",
              example = "2030-03-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return service.availability(slug, serviceId, staffId, from, to);
  }
}
