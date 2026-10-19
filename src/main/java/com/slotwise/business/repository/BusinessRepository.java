package com.slotwise.business.repository;

import com.slotwise.business.entity.Business;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

  Optional<Business> findBySlug(String slug);

  boolean existsBySlug(String slug);

  List<Business> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
