package com.slotwise.offering.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class OfferingDtos {

  private OfferingDtos() {}

  public record CreateOfferingRequest(
      @Schema(example = "Dental check-up") @NotBlank @Size(max = 120) String name,
      @Schema(example = "30") @NotNull @Min(5) @Max(1440) Integer durationMinutes,
      @Schema(example = "60.00") @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
          BigDecimal price,
      @Schema(example = "EUR") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @Schema(example = "0") @Min(0) @Max(240) Integer bufferBefore,
      @Schema(example = "10") @Min(0) @Max(240) Integer bufferAfter) {}

  public record UpdateOfferingRequest(
      @Size(min = 1, max = 120) String name,
      @Min(5) @Max(1440) Integer durationMinutes,
      @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @Min(0) @Max(240) Integer bufferBefore,
      @Min(0) @Max(240) Integer bufferAfter,
      Boolean active) {}

  public record OfferingResponse(
      UUID id,
      UUID businessId,
      String name,
      int durationMinutes,
      BigDecimal price,
      String currency,
      int bufferBefore,
      int bufferAfter,
      boolean active) {}
}
