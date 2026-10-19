package com.slotwise.staff.service;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.staff.dto.StaffDtos.CreateTimeOffRequest;
import com.slotwise.staff.dto.StaffDtos.SetWorkingHoursRequest;
import com.slotwise.staff.dto.StaffDtos.TimeOffResponse;
import com.slotwise.staff.dto.StaffDtos.WorkingHoursEntry;
import com.slotwise.staff.dto.StaffDtos.WorkingHoursResponse;
import com.slotwise.staff.entity.TimeOff;
import com.slotwise.staff.entity.TimeOffKind;
import com.slotwise.staff.entity.WorkingHours;
import com.slotwise.staff.mapper.StaffMapper;
import com.slotwise.staff.repository.TimeOffRepository;
import com.slotwise.staff.repository.WorkingHoursRepository;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Weekly working hours and time off / extra working windows for a staff member. */
@Service
public class ScheduleService {

  private final WorkingHoursRepository workingHours;
  private final TimeOffRepository timeOff;
  private final BusinessAccess access;
  private final StaffMapper mapper;

  public ScheduleService(
      WorkingHoursRepository workingHours,
      TimeOffRepository timeOff,
      BusinessAccess access,
      StaffMapper mapper) {
    this.workingHours = workingHours;
    this.timeOff = timeOff;
    this.access = access;
    this.mapper = mapper;
  }

  /** Replaces the whole week. Ranges on the same day must not overlap. */
  @Transactional
  public WorkingHoursResponse setWorkingHours(
      UUID staffId, SetWorkingHoursRequest req, AuthUser owner) {
    access.requireStaffOfOwner(staffId, owner);
    validateRanges(req.entries());
    workingHours.deleteByStaffId(staffId);
    List<WorkingHours> rows =
        req.entries().stream()
            .map(e -> new WorkingHours(staffId, e.dayOfWeek(), e.start(), e.end()))
            .toList();
    workingHours.saveAll(rows);
    return toResponse(staffId, rows);
  }

  @Transactional(readOnly = true)
  public WorkingHoursResponse getWorkingHours(UUID staffId, AuthUser user) {
    access.requireStaffOwnerOrSelf(staffId, user);
    return toResponse(staffId, workingHours.findByStaffId(staffId));
  }

  @Transactional
  public TimeOffResponse addTimeOff(UUID staffId, CreateTimeOffRequest req, AuthUser user) {
    access.requireStaffOwnerOrSelf(staffId, user);
    if (!req.startsAt().isBefore(req.endsAt())) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "startsAt must be before endsAt");
    }
    TimeOffKind kind = req.kind() == null ? TimeOffKind.OFF : req.kind();
    return mapper.toResponse(
        timeOff.save(new TimeOff(staffId, kind, req.startsAt(), req.endsAt(), req.reason())));
  }

  @Transactional(readOnly = true)
  public List<TimeOffResponse> listTimeOff(UUID staffId, AuthUser user) {
    access.requireStaffOwnerOrSelf(staffId, user);
    return timeOff.findByStaffIdOrderByStartsAtAsc(staffId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public void deleteTimeOff(UUID staffId, UUID timeOffId, AuthUser user) {
    access.requireStaffOwnerOrSelf(staffId, user);
    TimeOff entry =
        timeOff.findById(timeOffId).orElseThrow(() -> ApiException.notFound("Time off"));
    if (!entry.getStaffId().equals(staffId)) {
      throw ApiException.notFound("Time off");
    }
    timeOff.delete(entry);
  }

  private WorkingHoursResponse toResponse(UUID staffId, List<WorkingHours> rows) {
    return new WorkingHoursResponse(
        staffId,
        rows.stream()
            .sorted(
                Comparator.comparing(WorkingHours::getDayOfWeek)
                    .thenComparing(WorkingHours::getStartTime))
            .map(mapper::toEntry)
            .toList());
  }

  private static void validateRanges(List<WorkingHoursEntry> entries) {
    for (WorkingHoursEntry e : entries) {
      if (!e.start().isBefore(e.end())) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "start must be before end on " + e.dayOfWeek());
      }
    }
    for (DayOfWeek day : DayOfWeek.values()) {
      List<WorkingHoursEntry> sameDay =
          entries.stream()
              .filter(e -> e.dayOfWeek() == day)
              .sorted(Comparator.comparing(WorkingHoursEntry::start))
              .toList();
      for (int i = 1; i < sameDay.size(); i++) {
        if (sameDay.get(i).start().isBefore(sameDay.get(i - 1).end())) {
          throw new ApiException(ErrorCode.VALIDATION_FAILED, "Overlapping ranges on " + day);
        }
      }
    }
  }
}
