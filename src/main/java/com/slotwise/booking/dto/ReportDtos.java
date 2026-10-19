package com.slotwise.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReportDtos {

  private ReportDtos() {}

  public record DayCount(LocalDate date, long bookings) {}

  public record StatsResponse(
      LocalDate from,
      LocalDate to,
      String timeZone,
      long totalBookings,
      long confirmed,
      long completed,
      long noShow,
      long cancelled,
      @Schema(description = "cancelled / total, 0..1") double cancellationRate,
      @Schema(description = "no-show / total, 0..1") double noShowRate,
      @Schema(description = "Sum of prices of confirmed and completed bookings, per currency")
          Map<String, BigDecimal> revenueEstimate,
      @Schema(description = "Non-cancelled bookings per local day, zero-filled")
          List<DayCount> bookingsPerDay) {}

  public record CalendarLinkResponse(String url) {}
}
