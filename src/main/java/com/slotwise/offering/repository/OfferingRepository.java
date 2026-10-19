package com.slotwise.offering.repository;

import com.slotwise.offering.entity.Offering;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferingRepository extends JpaRepository<Offering, UUID> {

  List<Offering> findByBusinessIdOrderByNameAsc(UUID businessId);

  List<Offering> findByBusinessIdAndActiveTrueOrderByNameAsc(UUID businessId);

  List<Offering> findByIdInAndBusinessId(Collection<UUID> ids, UUID businessId);
}
