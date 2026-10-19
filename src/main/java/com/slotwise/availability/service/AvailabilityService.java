package com.slotwise.availability.service;

import com.slotwise.availability.calc.AvailabilityCalculator;
import com.slotwise.availability.calc.AvailabilityQuery;
import com.slotwise.availability.calc.Slot;
import com.slotwise.availability.calc.StaffSchedule;
import com.slotwise.availability.calc.StaffSchedule.WeeklyRange;
import com.slotwise.availability.dto.AvailabilityDtos.AvailabilityResponse;
import com.slotwise.availability.dto.AvailabilityDtos.DayAvailability;
import com.slotwise.availability.dto.AvailabilityDtos.SlotDto;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.common.time.TimeInterval;
import com.slotwise.offering.entity.Offering;
import com.slotwise.offering.repository.OfferingRepository;
import com.slotwise.staff.entity.Staff;
import com.slotwise.staff.entity.TimeOff;
import com.slotwise.staff.entity.TimeOffKind;
import com.slotwise.staff.entity.WorkingHours;
import com.slotwise.staff.repository.StaffRepository;
import com.slotwise.staff.repository.TimeOffRepository;
import com.slotwise.staff.repository.WorkingHoursRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the data for a public availability request and hands it to the pure calculator. */
@Service
public class AvailabilityService {

  public static final int MAX_RANGE_DAYS = 62;

  private final BusinessRepository businesses;
  private final OfferingRepository offerings;
  private final StaffRepository staff;
  private final WorkingHoursRepository workingHours;
  private final TimeOffRepository timeOff;
  private final BusyTimeSource busySource;
  private final Clock clock;
  private final AvailabilityCalculator calculator = new AvailabilityCalculator();

  public AvailabilityService(
      BusinessRepository businesses,
      OfferingRepository offerings,
      StaffRepository staff,
      WorkingHoursRepository workingHours,
      TimeOffRepository timeOff,
      BusyTimeSource busySource,
      Clock clock) {
    this.businesses = businesses;
    this.offerings = offerings;
    this.staff = staff;
    this.workingHours = workingHours;
    this.timeOff = timeOff;
    this.busySource = busySource;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AvailabilityResponse availability(
      String slug, UUID serviceId, UUID staffId, LocalDate from, LocalDate to) {
    if (to.isBefore(from)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "'to' must not be before 'from'");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Range may span at most " + MAX_RANGE_DAYS + " days");
    }
    Business business =
        businesses.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Business"));
    Offering offering =
        offerings
            .findById(serviceId)
            .filter(o -> o.isActive() && o.getBusinessId().equals(business.getId()))
            .orElseThrow(() -> ApiException.notFound("Service"));

    List<Staff> qualified = qualifiedStaff(business, offering, staffId);
    if (staffId != null && qualified.isEmpty()) {
      throw new ApiException(
          ErrorCode.NOT_FOUND, "Staff member not found or does not perform this service");
    }

    Map<LocalDate, List<Slot>> slots =
        compute(business, offering, qualified, from, to, clock.instant(), null);
    List<DayAvailability> days =
        slots.entrySet().stream()
            .map(
                e ->
                    new DayAvailability(
                        e.getKey(), e.getValue().stream().map(sl -> toDto(sl, business)).toList()))
            .toList();
    return new AvailabilityResponse(offering.getId(), business.getTimeZone(), from, to, days);
  }

  /**
   * Staff members who are free for exactly this start time, according to the same rules as the
   * public availability listing (grid, working hours, policies, bookings, holds). Used to validate
   * holds and reschedules; {@code excludeBookingId} lets a booking move over its own old slot.
   */
  @Transactional(readOnly = true)
  public List<UUID> freeStaffAt(
      Business business,
      Offering offering,
      Instant start,
      UUID staffIdOrNull,
      UUID excludeBookingId) {
    List<Staff> qualified = qualifiedStaff(business, offering, staffIdOrNull);
    if (qualified.isEmpty()) {
      return List.of();
    }
    LocalDate date = start.atZone(business.zone()).toLocalDate();
    return compute(business, offering, qualified, date, date, clock.instant(), excludeBookingId)
        .getOrDefault(date, List.of())
        .stream()
        .filter(s -> s.start().equals(start))
        .findFirst()
        .map(Slot::staffIds)
        .orElse(List.of());
  }

  private List<Staff> qualifiedStaff(Business business, Offering offering, UUID staffIdOrNull) {
    return staff.findByBusinessIdAndActiveTrueOrderByNameAsc(business.getId()).stream()
        .filter(s -> s.getOfferingIds().contains(offering.getId()))
        .filter(s -> staffIdOrNull == null || s.getId().equals(staffIdOrNull))
        .toList();
  }

  private Map<LocalDate, List<Slot>> compute(
      Business business,
      Offering offering,
      List<Staff> qualified,
      LocalDate from,
      LocalDate to,
      Instant now,
      UUID excludeBookingId) {
    List<StaffSchedule> schedules = schedules(business, qualified, from, to, now, excludeBookingId);
    return calculator.calculate(
        new AvailabilityQuery(
            business.zone(),
            now,
            Duration.ofMinutes(business.getMinNoticeMinutes()),
            business.getMaxAdvanceDays(),
            business.getSlotIntervalMinutes(),
            offering.duration(),
            Duration.ofMinutes(offering.getBufferBefore()),
            Duration.ofMinutes(offering.getBufferAfter()),
            from,
            to,
            schedules));
  }

  private List<StaffSchedule> schedules(
      Business business,
      List<Staff> members,
      LocalDate from,
      LocalDate to,
      Instant now,
      UUID excludeBookingId) {
    if (members.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = members.stream().map(Staff::getId).toList();
    // pad the window by a day on both sides: offsets differ from UTC and buffers reach outside
    Instant windowStart = from.minusDays(1).atStartOfDay(business.zone()).toInstant();
    Instant windowEnd = to.plusDays(2).atStartOfDay(business.zone()).toInstant();
    TimeInterval window = new TimeInterval(windowStart, windowEnd);

    Map<UUID, List<WorkingHours>> hours =
        workingHours.findByStaffIdIn(ids).stream()
            .collect(Collectors.groupingBy(WorkingHours::getStaffId));
    Map<UUID, List<TimeOff>> off =
        timeOff.findOverlapping(ids, windowStart, windowEnd).stream()
            .collect(Collectors.groupingBy(TimeOff::getStaffId));
    Map<UUID, List<TimeInterval>> busy = busySource.busy(ids, window, now, excludeBookingId);

    return members.stream()
        .map(
            m -> {
              List<TimeOff> entries = off.getOrDefault(m.getId(), List.of());
              return new StaffSchedule(
                  m.getId(),
                  hours.getOrDefault(m.getId(), List.of()).stream()
                      .map(h -> new WeeklyRange(h.getDayOfWeek(), h.getStartTime(), h.getEndTime()))
                      .toList(),
                  intervals(entries, TimeOffKind.EXTRA),
                  intervals(entries, TimeOffKind.OFF),
                  busy.getOrDefault(m.getId(), List.of()));
            })
        .toList();
  }

  private static List<TimeInterval> intervals(Collection<TimeOff> entries, TimeOffKind kind) {
    return entries.stream()
        .filter(t -> t.getKind() == kind)
        .map(t -> new TimeInterval(t.getStartsAt(), t.getEndsAt()))
        .toList();
  }

  private static SlotDto toDto(Slot slot, Business business) {
    return new SlotDto(
        slot.start().atZone(business.zone()).toOffsetDateTime(),
        slot.end().atZone(business.zone()).toOffsetDateTime(),
        slot.staffIds());
  }
}
