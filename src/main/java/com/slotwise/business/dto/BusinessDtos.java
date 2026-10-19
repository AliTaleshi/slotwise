package com.slotwise.business.dto;

import com.slotwise.common.validation.ValidTimeZone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BusinessDtos {

  private BusinessDtos() {}

  public static final String SLOT_INTERVALS = "5,10,15,20,30,60";

  public record CreateBusinessRequest(
      @Schema(example = "Sunrise Dental") @NotBlank @Size(max = 120) String name,
      @Schema(example = "sunrise-dental", description = "URL-safe public identifier")
          @NotBlank
          @Size(min = 3, max = 60)
          @Pattern(
              regexp = "[a-z0-9]+(-[a-z0-9]+)*",
              message = "lowercase letters, digits and hyphens only")
          String slug,
      @Schema(example = "Europe/Berlin") @NotBlank @ValidTimeZone String timeZone,
      @Email @Size(max = 254) String contactEmail,
      @Size(max = 40) String contactPhone,
      @Schema(example = "120") @Min(0) @Max(10080) Integer minNoticeMinutes,
      @Schema(example = "60") @Min(1) @Max(730) Integer maxAdvanceDays,
      @Schema(example = "1440") @Min(0) @Max(43200) Integer cancelCutoffMinutes,
      @Schema(example = "15", description = "One of 5, 10, 15, 20, 30, 60") @Min(5) @Max(60)
          Integer slotIntervalMinutes) {}

  /** PATCH body: null means "leave unchanged". The slug is immutable. */
  public record UpdateBusinessRequest(
      @Size(min = 1, max = 120) String name,
      @ValidTimeZone String timeZone,
      @Email @Size(max = 254) String contactEmail,
      @Size(max = 40) String contactPhone,
      @Min(0) @Max(10080) Integer minNoticeMinutes,
      @Min(1) @Max(730) Integer maxAdvanceDays,
      @Min(0) @Max(43200) Integer cancelCutoffMinutes,
      @Min(5) @Max(60) Integer slotIntervalMinutes) {}

  public record BusinessResponse(
      UUID id,
      String name,
      String slug,
      String timeZone,
      String contactEmail,
      String contactPhone,
      int minNoticeMinutes,
      int maxAdvanceDays,
      int cancelCutoffMinutes,
      int slotIntervalMinutes) {}

  public record PublicService(
      UUID id, String name, int durationMinutes, BigDecimal price, String currency) {}

  public record PublicStaff(UUID id, String name, Set<UUID> serviceIds) {}

  public record PublicBusinessResponse(
      String name,
      String slug,
      String timeZone,
      String contactEmail,
      String contactPhone,
      int minNoticeMinutes,
      int maxAdvanceDays,
      int cancelCutoffMinutes,
      int slotIntervalMinutes,
      List<PublicService> services,
      List<PublicStaff> staff) {}
}
