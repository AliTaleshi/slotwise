package com.slotwise.booking.service;

import com.slotwise.availability.service.AvailabilityService;
import com.slotwise.booking.dto.BookingDtos.CreateHoldRequest;
import com.slotwise.booking.dto.BookingDtos.HoldResponse;
import com.slotwise.booking.entity.Hold;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.booking.repository.HoldRepository;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ConstraintViolations;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.offering.entity.Offering;
import com.slotwise.offering.repository.OfferingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step one of booking: reserve a slot for a few minutes.
 *
 * <p>Exclusivity comes from the {@code holds_no_overlap} exclusion constraint, not from locks in
 * this class. Each attempt runs in its own transaction so that a constraint violation for one
 * candidate staff member does not poison the next attempt.
 */
@Service
public class HoldService {

  private static final Logger log = LoggerFactory.getLogger(HoldService.class);

  private final OfferingRepository offerings;
  private final BusinessRepository businesses;
  private final HoldRepository holds;
  private final BookingRepository bookings;
  private final AvailabilityService availability;
  private final BookingProperties props;
  private final Clock clock;
  private final TransactionTemplate tx;

  public HoldService(
      OfferingRepository offerings,
      BusinessRepository businesses,
      HoldRepository holds,
      BookingRepository bookings,
      AvailabilityService availability,
      BookingProperties props,
      Clock clock,
      PlatformTransactionManager txManager) {
    this.offerings = offerings;
    this.businesses = businesses;
    this.holds = holds;
    this.bookings = bookings;
    this.availability = availability;
    this.props = props;
    this.clock = clock;
    this.tx = new TransactionTemplate(txManager);
  }

  public HoldResponse create(CreateHoldRequest req) {
    Offering offering =
        offerings
            .findById(req.serviceId())
            .filter(Offering::isActive)
            .orElseThrow(() -> ApiException.notFound("Service"));
    Business business =
        businesses
            .findById(offering.getBusinessId())
            .orElseThrow(() -> ApiException.notFound("Business"));

    List<UUID> candidates =
        availability.freeStaffAt(business, offering, req.start(), req.staffId(), null);
    for (UUID staffId : candidates) {
      try {
        Hold hold = tx.execute(status -> insertHold(business, offering, staffId, req.start()));
        return toResponse(hold, business);
      } catch (DataIntegrityViolationException e) {
        if (!ConstraintViolations.isExclusionViolation(e)) {
          throw e;
        }
        log.debug("Hold lost the race for staff {} at {}", staffId, req.start());
      } catch (ApiException e) {
        if (e.code() != ErrorCode.SLOT_UNAVAILABLE) {
          throw e;
        }
      }
    }
    throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "This time slot is no longer available");
  }

  private Hold insertHold(Business business, Offering offering, UUID staffId, Instant start) {
    Instant now = clock.instant();
    Instant end = start.plus(offering.duration());
    Instant blockedFrom = start.minusSeconds(offering.getBufferBefore() * 60L);
    Instant blockedTo = end.plusSeconds(offering.getBufferAfter() * 60L);

    // Expired-but-not-yet-cleaned holds still occupy the constraint; clear them out of the way.
    holds.deleteExpiredOverlapping(staffId, now, blockedFrom, blockedTo);

    Hold hold =
        holds.saveAndFlush(
            new Hold(
                business.getId(),
                staffId,
                offering.getId(),
                start,
                end,
                blockedFrom,
                blockedTo,
                now.plus(props.holdTtl()),
                now));

    // Holds and bookings are different tables, so the constraint cannot compare them. Check after
    // inserting: a booking committed while we waited on the constraint is visible to this
    // statement.
    if (bookings.existsActiveOverlapping(staffId, blockedFrom, blockedTo, new UUID(0, 0))) {
      throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "This time slot is no longer available");
    }
    return hold;
  }

  private HoldResponse toResponse(Hold hold, Business business) {
    return new HoldResponse(
        hold.getId(),
        hold.getOfferingId(),
        hold.getStaffId(),
        hold.getStartsAt().atZone(business.zone()).toOffsetDateTime(),
        hold.getEndsAt().atZone(business.zone()).toOffsetDateTime(),
        hold.getExpiresAt().atZone(business.zone()).toOffsetDateTime());
  }
}
