package com.slotwise.staff.mapper;

import com.slotwise.business.dto.BusinessDtos.PublicStaff;
import com.slotwise.staff.dto.StaffDtos.StaffResponse;
import com.slotwise.staff.dto.StaffDtos.TimeOffResponse;
import com.slotwise.staff.dto.StaffDtos.WorkingHoursEntry;
import com.slotwise.staff.entity.Staff;
import com.slotwise.staff.entity.TimeOff;
import com.slotwise.staff.entity.WorkingHours;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StaffMapper {

  @Mapping(target = "serviceIds", source = "offeringIds")
  StaffResponse toResponse(Staff staff);

  @Mapping(target = "serviceIds", source = "offeringIds")
  PublicStaff toPublic(Staff staff);

  @Mapping(target = "start", source = "startTime")
  @Mapping(target = "end", source = "endTime")
  WorkingHoursEntry toEntry(WorkingHours hours);

  TimeOffResponse toResponse(TimeOff timeOff);

  /** Detaches Hibernate's lazy collection so DTOs stay plain. */
  default Set<UUID> copy(Set<UUID> ids) {
    return ids == null ? Set.of() : new HashSet<>(ids);
  }
}
