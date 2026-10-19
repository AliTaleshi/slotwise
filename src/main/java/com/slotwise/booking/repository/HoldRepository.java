package com.slotwise.booking.repository;

import com.slotwise.booking.entity.Hold;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from Hold h where h.id = :id")
  Optional<Hold> findByIdForUpdate(UUID id);

  /**
   * Removes expired holds that would otherwise block a new hold through the exclusion constraint.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from Hold h where h.staffId = :staffId and h.expiresAt <= :now"
          + " and h.blockedFrom < :to and h.blockedTo > :from")
  int deleteExpiredOverlapping(UUID staffId, Instant now, Instant from, Instant to);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Hold h where h.expiresAt <= :now")
  int deleteExpired(Instant now);
}
