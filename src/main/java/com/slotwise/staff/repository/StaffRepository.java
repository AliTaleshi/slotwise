package com.slotwise.staff.repository;

import com.slotwise.staff.entity.Staff;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

  List<Staff> findByBusinessIdOrderByNameAsc(UUID businessId);

  List<Staff> findByBusinessIdAndActiveTrueOrderByNameAsc(UUID businessId);

  Optional<Staff> findByUserId(UUID userId);
}
