package com.slotwise.booking.repository;

import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.entity.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository
    extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

  List<Booking>
      findByStaffIdAndStatusNotAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
          UUID staffId, BookingStatus status, Instant from, Instant to);

  Optional<Booking> findByBusinessIdAndIdempotencyKey(UUID businessId, String idempotencyKey);

  Optional<Booking> findByHoldId(UUID holdId);

  /** Non-cancelled bookings of the staff member whose blocked range overlaps [from, to). */
  @Query(
      "select (count(b) > 0) from Booking b where b.staffId = :staffId and b.status <> :cancelled"
          + " and b.blockedFrom < :to and b.blockedTo > :from and b.id <> :excludeId")
  boolean existsOverlapping(
      UUID staffId, Instant from, Instant to, BookingStatus cancelled, UUID excludeId);

  default boolean existsActiveOverlapping(UUID staffId, Instant from, Instant to, UUID excludeId) {
    return existsOverlapping(staffId, from, to, BookingStatus.CANCELLED, excludeId);
  }

  @Query(
      "select b from Booking b where b.staffId in :staffIds and b.status <> com.slotwise.booking.entity.BookingStatus.CANCELLED"
          + " and b.blockedFrom < :to and b.blockedTo > :from and b.id <> :excludeId")
  List<Booking> findActiveOverlapping(
      Collection<UUID> staffIds, Instant from, Instant to, UUID excludeId);
}
