package com.slotwise.staff.dto;

import com.slotwise.staff.entity.TimeOffKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StaffDtos {

  private StaffDtos() {}

  public record CreateStaffRequest(
      @Schema(example = "Dr. Mia Keller") @NotBlank @Size(max = 120) String name,
      @Schema(example = "mia@example.com") @Email @Size(max = 254) String email,
      @Schema(description = "Services this person performs") Set<UUID> serviceIds,
      @Schema(description = "If set (with email), also creates a STAFF login for this person")
          @Size(min = 8, max = 72)
          String password) {}

  public record UpdateStaffRequest(
      @Size(min = 1, max = 120) String name,
      @Email @Size(max = 254) String email,
      Boolean active,
      Set<UUID> serviceIds) {}

  public record StaffResponse(
      UUID id,
      UUID businessId,
      UUID userId,
      String name,
      String email,
      boolean active,
      Set<UUID> serviceIds) {}

  public record WorkingHoursEntry(
      @Schema(example = "MONDAY") @NotNull DayOfWeek dayOfWeek,
      @Schema(example = "09:00", type = "string") @NotNull LocalTime start,
      @Schema(example = "13:00", type = "string") @NotNull LocalTime end) {}

  public record SetWorkingHoursRequest(@NotNull @Valid List<WorkingHoursEntry> entries) {}

  public record WorkingHoursResponse(UUID staffId, List<WorkingHoursEntry> entries) {}

  public record CreateTimeOffRequest(
      @Schema(description = "OFF (default) blocks time; EXTRA adds a working window")
          TimeOffKind kind,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @Size(max = 200) String reason) {}

  public record TimeOffResponse(
      UUID id, UUID staffId, TimeOffKind kind, Instant startsAt, Instant endsAt, String reason) {}
}
