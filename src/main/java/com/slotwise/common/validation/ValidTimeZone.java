package com.slotwise.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.DateTimeException;
import java.time.ZoneId;

/** An IANA time zone ID such as {@code Europe/Berlin}. */
@Documented
@Constraint(validatedBy = ValidTimeZone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {

  String message() default "must be a valid IANA time zone ID";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidTimeZone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      try {
        // Reject offsets like "+02:00" and short IDs like "CET"; require a region ID.
        return ZoneId.of(value).getRules() != null && ZoneId.getAvailableZoneIds().contains(value);
      } catch (DateTimeException e) {
        return false;
      }
    }
  }
}
