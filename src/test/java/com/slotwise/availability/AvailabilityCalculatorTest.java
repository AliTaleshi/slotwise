package com.slotwise.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotwise.availability.calc.AvailabilityCalculator;
import com.slotwise.availability.calc.AvailabilityQuery;
import com.slotwise.availability.calc.Slot;
import com.slotwise.availability.calc.StaffSchedule;
import com.slotwise.availability.calc.StaffSchedule.WeeklyRange;
import com.slotwise.common.time.TimeInterval;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

  static final ZoneId UTC = ZoneOffset.UTC;
  static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** Monday 2030-01-07. */
  static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

  static final UUID MIA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  static final UUID TOM = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  final AvailabilityCalculator calc = new AvailabilityCalculator();

  /** Mutable builder so each test reads as a scenario. */
  static class Scenario {
    ZoneId zone = UTC;
    Instant now = Instant.parse("2030-01-01T00:00:00Z");
    Duration minNotice = Duration.ZERO;
    int maxAdvanceDays = 365;
    int interval = 30;
    Duration duration = Duration.ofMinutes(60);
    Duration before = Duration.ZERO;
    Duration after = Duration.ZERO;
    LocalDate from = MONDAY;
    LocalDate to = MONDAY;
    List<StaffSchedule> staff = new ArrayList<>();

    Scenario staff(UUID id, List<WeeklyRange> weekly) {
      return staff(id, weekly, List.of(), List.of(), List.of());
    }

    Scenario staff(
        UUID id,
        List<WeeklyRange> weekly,
        List<TimeInterval> extra,
        List<TimeInterval> off,
        List<TimeInterval> busy) {
      staff.add(new StaffSchedule(id, weekly, extra, off, busy));
      return this;
    }

    AvailabilityQuery build() {
      return new AvailabilityQuery(
          zone, now, minNotice, maxAdvanceDays, interval, duration, before, after, from, to, staff);
    }
  }

  static WeeklyRange mon(String start, String end) {
    return new WeeklyRange(DayOfWeek.MONDAY, LocalTime.parse(start), LocalTime.parse(end));
  }

  static TimeInterval utc(String start, String end) {
    return new TimeInterval(Instant.parse(start), Instant.parse(end));
  }

  List<String> starts(Scenario s, LocalDate day) {
    return calc.calculate(s.build()).get(day).stream()
        .map(slot -> slot.start().atZone(s.zone).toLocalTime().toString())
        .toList();
  }

  @Test
  void cutsWorkingHoursIntoSlotsThatFitTheService() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "11:00")));
    // 60 min service, 30 min grid: last start is 10:00
    assertThat(starts(s, MONDAY)).containsExactly("09:00", "09:30", "10:00");
  }

  @Test
  void splitShiftLeavesBreakUnavailable() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "11:00"), mon("12:00", "14:00")));
    s.duration = Duration.ofMinutes(30);
    assertThat(starts(s, MONDAY))
        .containsExactly("09:00", "09:30", "10:00", "10:30", "12:00", "12:30", "13:00", "13:30");
  }

  @Test
  void dayWithoutWorkingHoursIsPresentButEmpty() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "11:00")));
    s.to = MONDAY.plusDays(1);
    Map<LocalDate, List<Slot>> result = calc.calculate(s.build());
    assertThat(result).containsKeys(MONDAY, MONDAY.plusDays(1));
    assertThat(result.get(MONDAY.plusDays(1))).isEmpty();
  }

  @Test
  void rangeStartingOffGridSnapsToNextGridPoint() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:10", "11:00")));
    s.interval = 15;
    s.duration = Duration.ofMinutes(30);
    assertThat(starts(s, MONDAY)).first().isEqualTo("09:15");
  }

  @Test
  void serviceLongerThanWindowYieldsNothing() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "09:45")));
    assertThat(starts(s, MONDAY)).isEmpty();
  }

  @Test
  void existingBookingRemovesOverlappingSlots() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "13:00")),
                List.of(),
                List.of(),
                List.of(utc("2030-01-07T10:00:00Z", "2030-01-07T11:00:00Z")));
    // 09:30 would run into the 10:00 booking, 11:00 is free again
    assertThat(starts(s, MONDAY)).containsExactly("09:00", "11:00", "11:30", "12:00");
  }

  @Test
  void bookingsOnlyBlockTheirOwnStaffMember() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "11:00")),
                List.of(),
                List.of(),
                List.of(utc("2030-01-07T09:00:00Z", "2030-01-07T11:00:00Z")))
            .staff(TOM, List.of(mon("09:00", "11:00")));
    List<Slot> slots = calc.calculate(s.build()).get(MONDAY);
    assertThat(slots)
        .hasSize(3)
        .allSatisfy(slot -> assertThat(slot.staffIds()).containsExactly(TOM));
  }

  @Test
  void anyStaffModeListsEveryFreeStaffMember() {
    Scenario s =
        new Scenario()
            .staff(MIA, List.of(mon("09:00", "10:00")))
            .staff(TOM, List.of(mon("09:00", "11:00")));
    List<Slot> slots = calc.calculate(s.build()).get(MONDAY);
    assertThat(slots.get(0).staffIds()).containsExactly(MIA, TOM);
    assertThat(slots.get(slots.size() - 1).staffIds()).containsExactly(TOM);
  }

  @Test
  void bufferAfterExistingBookingPushesNextSlot() {
    // service has 15 min buffers; existing booking's blocked range already includes its buffers
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "13:00")),
                List.of(),
                List.of(),
                List.of(utc("2030-01-07T09:45:00Z", "2030-01-07T11:15:00Z")));
    s.duration = Duration.ofMinutes(30);
    s.before = Duration.ofMinutes(15);
    s.after = Duration.ofMinutes(15);
    // 09:00 needs [08:45, 09:45): touches the booking's start, so it is free; 11:00 would need
    // [10:45, ...) and is blocked; 11:30 needs [11:15, 12:15) and touches the booking's end only
    assertThat(starts(s, MONDAY)).containsExactly("09:00", "11:30", "12:00", "12:30");
  }

  @Test
  void bufferBeforeIsRespectedAhead() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "12:00")),
                List.of(),
                List.of(),
                List.of(utc("2030-01-07T10:00:00Z", "2030-01-07T11:00:00Z")));
    s.duration = Duration.ofMinutes(30);
    s.before = Duration.ofMinutes(30);
    // 11:00 start needs [10:30, ...) which overlaps the booking; 11:30 needs [11:00, ...) is fine
    assertThat(starts(s, MONDAY)).containsExactly("09:00", "09:30", "11:30");
  }

  @Test
  void buffersMayExtendOutsideWorkingHours() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "10:00")));
    s.duration = Duration.ofMinutes(60);
    s.before = Duration.ofMinutes(15);
    s.after = Duration.ofMinutes(15);
    assertThat(starts(s, MONDAY)).containsExactly("09:00");
  }

  @Test
  void timeOffBlocksOnlyItsRange() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "13:00")),
                List.of(),
                List.of(utc("2030-01-07T10:00:00Z", "2030-01-07T11:00:00Z")),
                List.of());
    assertThat(starts(s, MONDAY)).containsExactly("09:00", "11:00", "11:30", "12:00");
  }

  @Test
  void wholeDayOffRemovesEverything() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "13:00")),
                List.of(),
                List.of(utc("2030-01-07T00:00:00Z", "2030-01-08T00:00:00Z")),
                List.of());
    assertThat(starts(s, MONDAY)).isEmpty();
  }

  @Test
  void extraWorkingWindowAddsAvailabilityOnADayOff() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(),
                List.of(utc("2030-01-07T14:00:00Z", "2030-01-07T16:00:00Z")),
                List.of(),
                List.of());
    assertThat(starts(s, MONDAY)).containsExactly("14:00", "14:30", "15:00");
  }

  @Test
  void adjacentExtraAndWeeklyWindowsAreMerged() {
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "10:00")),
                List.of(utc("2030-01-07T10:00:00Z", "2030-01-07T11:00:00Z")),
                List.of(),
                List.of());
    // 09:30-10:30 only fits because the two windows were merged
    assertThat(starts(s, MONDAY)).contains("09:30");
  }

  @Test
  void minimumNoticeHidesSoonSlots() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "13:00")));
    s.now = Instant.parse("2030-01-07T08:00:00Z");
    s.minNotice = Duration.ofHours(2);
    // earliest allowed start is 10:00 (inclusive)
    assertThat(starts(s, MONDAY)).containsExactly("10:00", "10:30", "11:00", "11:30", "12:00");
  }

  @Test
  void maximumAdvanceWindowHidesFarSlots() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "11:00")));
    s.from = MONDAY;
    s.to = MONDAY.plusDays(14);
    s.now = Instant.parse("2030-01-07T00:00:00Z");
    s.maxAdvanceDays = 7;
    Map<LocalDate, List<Slot>> result = calc.calculate(s.build());
    assertThat(result.get(MONDAY)).isNotEmpty();
    // exactly 7 days later at 09:00 is beyond now+7d (00:00), so the next Monday is hidden
    assertThat(result.get(MONDAY.plusDays(7))).isEmpty();
  }

  @Test
  void maximumAdvanceBoundaryIsExclusive() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "11:00")));
    s.from = MONDAY.plusDays(7);
    s.to = MONDAY.plusDays(7);
    s.now = Instant.parse("2030-01-07T09:30:00Z");
    s.maxAdvanceDays = 7;
    // limit is 2030-01-14T09:30Z: 09:00 allowed, 09:30 and later not
    assertThat(starts(s, MONDAY.plusDays(7))).containsExactly("09:00");
  }

  @Test
  void pastSlotsAreNeverOffered() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "11:00")));
    s.now = Instant.parse("2030-01-07T10:00:00Z");
    assertThat(starts(s, MONDAY)).containsExactly("10:00");
  }

  @Test
  void slotsUseBusinessZoneWallClockAndKeepOffsets() {
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "10:00")));
    s.zone = ZoneId.of("Asia/Tehran"); // UTC+3:30 in January 2030 (no DST since 2022)
    Slot first = calc.calculate(s.build()).get(MONDAY).get(0);
    assertThat(first.start()).isEqualTo(Instant.parse("2030-01-07T05:30:00Z"));
    assertThat(first.start().atZone(s.zone).getOffset().getTotalSeconds())
        .isEqualTo(3 * 3600 + 1800);
  }

  // ---- Daylight saving time (Europe/Berlin) ----

  /** 2030-03-31: clocks jump 02:00 -> 03:00; the day has 23 real hours. */
  @Test
  void springForwardNeverOffersNonexistentLocalTimes() {
    LocalDate day = LocalDate.of(2030, 3, 31);
    Scenario s = new Scenario();
    s.zone = BERLIN;
    s.from = day;
    s.to = day;
    s.staff(
        MIA, List.of(new WeeklyRange(DayOfWeek.SUNDAY, LocalTime.of(1, 0), LocalTime.of(5, 0))));
    // 01:00-05:00 wall clock is only 3 real hours; 02:00 and 02:30 do not exist
    assertThat(starts(s, day)).containsExactly("01:00", "01:30", "03:00", "03:30", "04:00");
    List<Slot> slots = calc.calculate(s.build()).get(day);
    assertThat(slots.get(1).start()).isEqualTo(Instant.parse("2030-03-31T00:30:00Z")); // 01:30 CET
    assertThat(slots.get(2).start()).isEqualTo(Instant.parse("2030-03-31T01:00:00Z")); // 03:00 CEST
  }

  /** 2030-10-27: clocks fall back 03:00 -> 02:00; 02:00-03:00 happens twice. */
  @Test
  void fallBackOffersRepeatedHourOnceWithEachOffset() {
    LocalDate day = LocalDate.of(2030, 10, 27);
    Scenario s = new Scenario();
    s.zone = BERLIN;
    s.from = day;
    s.to = day;
    s.interval = 60;
    s.staff(
        MIA, List.of(new WeeklyRange(DayOfWeek.SUNDAY, LocalTime.of(1, 0), LocalTime.of(4, 0))));
    List<Slot> slots = calc.calculate(s.build()).get(day);
    // 01:00 CEST, 02:00 CEST, 02:00 CET, 03:00 CET -> four real hours
    assertThat(slots)
        .extracting(Slot::start)
        .containsExactly(
            Instant.parse("2030-10-26T23:00:00Z"),
            Instant.parse("2030-10-27T00:00:00Z"),
            Instant.parse("2030-10-27T01:00:00Z"),
            Instant.parse("2030-10-27T02:00:00Z"));
    assertThat(slots.get(1).start().atZone(BERLIN).getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    assertThat(slots.get(2).start().atZone(BERLIN).getOffset()).isEqualTo(ZoneOffset.ofHours(1));
  }

  @Test
  void normalWorkingHoursKeepWallClockAcrossTheTransition() {
    // Mon 2030-03-25 is CET (+1), Mon 2030-04-01 is CEST (+2); 09:00 local both days
    Scenario s = new Scenario().staff(MIA, List.of(mon("09:00", "10:00")));
    s.zone = BERLIN;
    s.from = LocalDate.of(2030, 3, 25);
    s.to = LocalDate.of(2030, 4, 1);
    s.now = Instant.parse("2030-03-01T00:00:00Z");
    Map<LocalDate, List<Slot>> result = calc.calculate(s.build());
    assertThat(result.get(LocalDate.of(2030, 3, 25)).get(0).start())
        .isEqualTo(Instant.parse("2030-03-25T08:00:00Z"));
    assertThat(result.get(LocalDate.of(2030, 4, 1)).get(0).start())
        .isEqualTo(Instant.parse("2030-04-01T07:00:00Z"));
  }

  @Test
  void bookingExpressedInUtcBlocksTheRightLocalSlotInSummer() {
    // 10:00-11:00 Berlin summer time = 08:00-09:00Z
    Scenario s =
        new Scenario()
            .staff(
                MIA,
                List.of(mon("09:00", "12:00")),
                List.of(),
                List.of(),
                List.of(utc("2030-07-01T08:00:00Z", "2030-07-01T09:00:00Z")));
    s.zone = BERLIN;
    s.from = LocalDate.of(2030, 7, 1);
    s.to = LocalDate.of(2030, 7, 1);
    s.now = Instant.parse("2030-06-01T00:00:00Z");
    assertThat(starts(s, s.from)).containsExactly("09:00", "11:00");
  }
}
