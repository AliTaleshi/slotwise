package com.slotwise.booking.dto;

import com.slotwise.booking.entity.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class BookingDtos {

  private BookingDtos() {}

  public record CreateHoldRequest(
      @NotNull UUID serviceId,
      @Schema(description = "Optional; omit to let the system pick any free qualified staff member")
          UUID staffId,
      @Schema(example = "2030-01-14T09:00:00+01:00", description = "Start of a listed slot")
          @NotNull
          Instant start) {}

  public record HoldResponse(
      UUID holdId,
      UUID serviceId,
      UUID staffId,
      OffsetDateTime start,
      OffsetDateTime end,
      @Schema(description = "The slot is released if the booking is not confirmed by then")
          OffsetDateTime expiresAt) {}

  public record CreateBookingRequest(
      @NotNull UUID holdId,
      @Schema(example = "Sam Guest", description = "Required for guests; defaults to the profile")
          @Size(max = 120)
          String guestName,
      @Schema(example = "sam@example.com") @Email @Size(max = 254) String guestEmail,
      @Size(max = 40) String guestPhone) {}

  public record RescheduleRequest(
      @NotNull Instant newStart,
      @Schema(description = "Optional; defaults to the current staff member when free")
          UUID staffId) {}

  public record StatusUpdateRequest(
      @Schema(description = "COMPLETED or NO_SHOW") @NotNull BookingStatus status) {}

  public record BookingResponse(
      UUID id,
      UUID businessId,
      BookingStatus status,
      UUID staffId,
      String staffName,
      UUID serviceId,
      String serviceName,
      String timeZone,
      OffsetDateTime start,
      OffsetDateTime end,
      BigDecimal price,
      String currency,
      String guestName,
      String guestEmail,
      String guestPhone,
      UUID customerId,
      long version,
      Instant createdAt,
      @Schema(description = "Signed token for the manage link; only present on create/reschedule")
          String manageToken,
      @Schema(description = "Link to view, reschedule or cancel without an account")
          String manageUrl) {}
}
