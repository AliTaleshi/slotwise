package com.slotwise.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AvailabilityDtos {

  private AvailabilityDtos() {}

  @Schema(description = "One bookable start time, expressed in the business time zone")
  public record SlotDto(
      @Schema(example = "2030-03-31T09:00:00+02:00") OffsetDateTime start,
      @Schema(example = "2030-03-31T09:30:00+02:00") OffsetDateTime end,
      @Schema(description = "Staff members free for this slot") List<UUID> staffIds) {}

  public record DayAvailability(
      @Schema(example = "2030-03-31", description = "Local date in the business time zone")
          LocalDate date,
      List<SlotDto> slots) {}

  public record AvailabilityResponse(
      UUID serviceId,
      @Schema(example = "Europe/Berlin") String timeZone,
      LocalDate from,
      LocalDate to,
      List<DayAvailability> days) {}
}
