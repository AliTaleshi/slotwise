package com.slotwise.common.error;

import java.sql.SQLException;

/** Recognises PostgreSQL constraint failures by SQLState, wherever they are wrapped. */
public final class ConstraintViolations {

  /** exclusion_violation: raised by the no-overlap constraints on holds and bookings. */
  private static final String EXCLUSION_VIOLATION = "23P01";

  private static final String UNIQUE_VIOLATION = "23505";

  private ConstraintViolations() {}

  public static boolean isExclusionViolation(Throwable t) {
    return hasSqlState(t, EXCLUSION_VIOLATION);
  }

  public static boolean isUniqueViolation(Throwable t) {
    return hasSqlState(t, UNIQUE_VIOLATION);
  }

  private static boolean hasSqlState(Throwable t, String state) {
    for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
      if (c instanceof SQLException e && state.equals(e.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
