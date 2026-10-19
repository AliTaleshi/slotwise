package com.slotwise.availability.calc;

import com.slotwise.availability.calc.StaffSchedule.WeeklyRange;
import com.slotwise.common.time.TimeInterval;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure slot calculation, no I/O and no clock: {@code working hours + extra windows - time off -
 * bookings/holds - buffers}, cut into a grid and limited by notice and advance policies.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>The appointment itself ({@code [start, start + duration)}) must lie inside one working
 *       window and must not touch time off.
 *   <li>The appointment plus its buffers ({@code [start - before, end + after)}) must not overlap
 *       any busy interval. Busy intervals already include the buffers of their own bookings.
 *   <li>Wall-clock times are converted with the business zone's rules, so a DST day has 23 or 25
 *       real hours and nonexistent local times are never offered.
 *   <li>The grid is anchored at local midnight and stepped in real time; with intervals that divide
 *       60 minutes it stays aligned to the clock across DST changes.
 * </ul>
 */
public final class AvailabilityCalculator {

  /** Returns slots grouped by local date; every date in the range is present, possibly empty. */
  public Map<LocalDate, List<Slot>> calculate(AvailabilityQuery q) {
    Map<LocalDate, List<Slot>> result = new TreeMap<>();
    Instant earliest = q.now().plus(q.minNotice());
    Instant latest = q.now().atZone(q.zone()).plusDays(q.maxAdvanceDays()).toInstant();
    Duration step = Duration.ofMinutes(q.slotIntervalMinutes());

    Map<UUID, List<TimeInterval>> windows = new java.util.HashMap<>();
    for (StaffSchedule s : q.staff()) {
      windows.put(s.staffId(), workingWindows(s, q));
    }

    for (LocalDate date = q.from(); !date.isAfter(q.to()); date = date.plusDays(1)) {
      List<Slot> day = new ArrayList<>();
      Instant dayStart = date.atStartOfDay(q.zone()).toInstant();
      Instant dayEnd = date.plusDays(1).atStartOfDay(q.zone()).toInstant();
      for (Instant start = dayStart; start.isBefore(dayEnd); start = start.plus(step)) {
        if (start.isBefore(earliest) || !start.isBefore(latest)) {
          continue;
        }
        Instant end = start.plus(q.duration());
        TimeInterval appointment = new TimeInterval(start, end);
        TimeInterval blocked =
            new TimeInterval(start.minus(q.bufferBefore()), end.plus(q.bufferAfter()));
        List<UUID> free = new ArrayList<>();
        for (StaffSchedule s : q.staff()) {
          if (isFree(s, windows.get(s.staffId()), appointment, blocked)) {
            free.add(s.staffId());
          }
        }
        if (!free.isEmpty()) {
          day.add(new Slot(start, end, List.copyOf(free)));
        }
      }
      result.put(date, day);
    }
    return result;
  }

  private static boolean isFree(
      StaffSchedule s, List<TimeInterval> windows, TimeInterval appointment, TimeInterval blocked) {
    boolean inWindow = windows.stream().anyMatch(w -> w.contains(appointment));
    if (!inWindow) {
      return false;
    }
    return s.busy().stream().noneMatch(blocked::overlaps);
  }

  /** Weekly ranges for each date in range plus extra windows, minus time off, merged. */
  private static List<TimeInterval> workingWindows(StaffSchedule s, AvailabilityQuery q) {
    List<TimeInterval> working = new ArrayList<>();
    for (LocalDate d = q.from(); !d.isAfter(q.to()); d = d.plusDays(1)) {
      for (WeeklyRange r : s.weekly()) {
        if (r.day() == d.getDayOfWeek()) {
          Instant start = toInstant(d, r.start(), q.zone());
          Instant end = toInstant(d, r.end(), q.zone());
          if (start.isBefore(end)) {
            working.add(new TimeInterval(start, end));
          }
        }
      }
    }
    working.addAll(s.extra());
    List<TimeInterval> merged = merge(working);
    for (TimeInterval off : s.timeOff()) {
      merged = subtract(merged, off);
    }
    return merged;
  }

  private static Instant toInstant(LocalDate date, java.time.LocalTime time, ZoneId zone) {
    // In a DST gap this resolves to the instant just after the gap, in an overlap to the earlier
    // offset, which is the standard java.time behaviour.
    return ZonedDateTime.of(date, time, zone).toInstant();
  }

  static List<TimeInterval> merge(List<TimeInterval> intervals) {
    List<TimeInterval> sorted = new ArrayList<>(intervals);
    sorted.sort(Comparator.comparing(TimeInterval::start));
    List<TimeInterval> out = new ArrayList<>();
    for (TimeInterval i : sorted) {
      if (!out.isEmpty() && !i.start().isAfter(out.getLast().end())) {
        TimeInterval last = out.removeLast();
        Instant end = i.end().isAfter(last.end()) ? i.end() : last.end();
        out.add(new TimeInterval(last.start(), end));
      } else {
        out.add(i);
      }
    }
    return out;
  }

  static List<TimeInterval> subtract(List<TimeInterval> from, TimeInterval cut) {
    List<TimeInterval> out = new ArrayList<>();
    for (TimeInterval i : from) {
      if (!i.overlaps(cut)) {
        out.add(i);
        continue;
      }
      if (i.start().isBefore(cut.start())) {
        out.add(new TimeInterval(i.start(), cut.start()));
      }
      if (i.end().isAfter(cut.end())) {
        out.add(new TimeInterval(cut.end(), i.end()));
      }
    }
    return out;
  }
}
