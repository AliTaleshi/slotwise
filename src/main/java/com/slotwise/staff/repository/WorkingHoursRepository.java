package com.slotwise.staff.repository;

import com.slotwise.staff.entity.WorkingHours;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

  List<WorkingHours> findByStaffId(UUID staffId);

  List<WorkingHours> findByStaffIdIn(Collection<UUID> staffIds);

  void deleteByStaffId(UUID staffId);
}
