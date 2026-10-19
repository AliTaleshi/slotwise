package com.slotwise.availability.calc;

import com.slotwise.common.time.TimeInterval;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Everything the calculator needs to know about one staff member.
 *
 * @param weekly weekly working ranges in the business's local wall-clock time
 * @param extra additional working windows (absolute instants)
 * @param timeOff windows the person is unavailable (absolute instants)
 * @param busy time already taken by bookings and unexpired holds, buffers included
 */
public record StaffSchedule(
    UUID staffId,
    List<WeeklyRange> weekly,
    List<TimeInterval> extra,
    List<TimeInterval> timeOff,
    List<TimeInterval> busy) {

  public record WeeklyRange(DayOfWeek day, LocalTime start, LocalTime end) {}
}
