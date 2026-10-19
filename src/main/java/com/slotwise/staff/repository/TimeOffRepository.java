package com.slotwise.staff.repository;

import com.slotwise.staff.entity.TimeOff;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {

  List<TimeOff> findByStaffIdOrderByStartsAtAsc(UUID staffId);

  /** Entries overlapping [from, to) for any of the given staff. */
  @Query(
      "select t from TimeOff t where t.staffId in :staffIds and t.startsAt < :to and t.endsAt > :from")
  List<TimeOff> findOverlapping(Collection<UUID> staffIds, Instant from, Instant to);
}
