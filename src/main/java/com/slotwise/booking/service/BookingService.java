package com.slotwise.booking.service;

import com.slotwise.auth.entity.Role;
import com.slotwise.auth.entity.User;
import com.slotwise.auth.repository.UserRepository;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.availability.service.AvailabilityService;
import com.slotwise.booking.dto.BookingDtos.BookingResponse;
import com.slotwise.booking.dto.BookingDtos.CreateBookingRequest;
import com.slotwise.booking.dto.BookingDtos.RescheduleRequest;
import com.slotwise.booking.entity.Booking;
import com.slotwise.booking.entity.BookingStatus;
import com.slotwise.booking.entity.Hold;
import com.slotwise.booking.event.BookingEvents.BookingCancelled;
import com.slotwise.booking.event.BookingEvents.BookingCreated;
import com.slotwise.booking.event.BookingEvents.BookingRescheduled;
import com.slotwise.booking.event.BookingEvents.BookingStatusChanged;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.booking.repository.HoldRepository;
import com.slotwise.booking.service.BookingAccess.Caller;
import com.slotwise.booking.service.BookingAccess.Capacity;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ConstraintViolations;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.offering.entity.Offering;
import com.slotwise.offering.repository.OfferingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirm, view, reschedule, cancel and complete bookings.
 *
 * <p>Writes run in explicit transactions so that database constraint violations (a lost race for a
 * slot, a replayed idempotency key) are caught <em>after</em> rollback and translated into clean
 * API errors instead of leaving a poisoned transaction behind.
 */
@Service
public class BookingService {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final HoldRepository holds;
  private final BookingRepository bookings;
  private final OfferingRepository offerings;
  private final BusinessRepository businesses;
  private final UserRepository users;
  private final AvailabilityService availability;
  private final BookingAccess access;
  private final BookingViews views;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final TransactionTemplate tx;

  public BookingService(
      HoldRepository holds,
      BookingRepository bookings,
      OfferingRepository offerings,
      BusinessRepository businesses,
      UserRepository users,
      AvailabilityService availability,
      BookingAccess access,
      BookingViews views,
      ApplicationEventPublisher events,
      Clock clock,
      PlatformTransactionManager txManager) {
    this.holds = holds;
    this.bookings = bookings;
    this.offerings = offerings;
    this.businesses = businesses;
    this.users = users;
    this.availability = availability;
    this.access = access;
    this.views = views;
    this.events = events;
    this.clock = clock;
    this.tx = new TransactionTemplate(txManager);
  }

  // ---- create ----

  public BookingResponse confirm(CreateBookingRequest req, String idempotencyKey, AuthUser user) {
    String key = normalizeKey(idempotencyKey);
    Optional<Booking> replay = findReplay(req.holdId(), key);
    if (replay.isPresent()) {
      return views.view(replay.get(), true);
    }
    try {
      Booking created = tx.execute(status -> doConfirm(req, key, user));
      return views.view(created, true);
    } catch (DataIntegrityViolationException e) {
      if (ConstraintViolations.isExclusionViolation(e)) {
        throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "This time slot is no longer available");
      }
      if (ConstraintViolations.isUniqueViolation(e)) {
        // a concurrent retry with the same hold or key won; answer with its result
        return findReplay(req.holdId(), key)
            .map(b -> views.view(b, true))
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "Duplicate booking request"));
      }
      throw e;
    } catch (ApiException e) {
      if (e.code() == ErrorCode.HOLD_EXPIRED) {
        Optional<Booking> winner = findReplay(req.holdId(), key);
        if (winner.isPresent()) {
          return views.view(winner.get(), true);
        }
      }
      throw e;
    }
  }

  private Booking doConfirm(CreateBookingRequest req, String key, AuthUser user) {
    Hold hold =
        holds
            .findByIdForUpdate(req.holdId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.HOLD_EXPIRED, "The hold does not exist or has expired"));
    Instant now = clock.instant();
    if (hold.isExpired(now)) {
      throw new ApiException(ErrorCode.HOLD_EXPIRED, "The hold has expired; pick a slot again");
    }
    Offering offering = offerings.findById(hold.getOfferingId()).orElseThrow();

    String name = req.guestName();
    String email = req.guestEmail();
    UUID customerId = null;
    if (user != null && user.role() == Role.CUSTOMER) {
      customerId = user.id();
      User profile = users.findById(user.id()).orElseThrow();
      name = isBlank(name) ? profile.getName() : name;
      email = isBlank(email) ? profile.getEmail() : email;
    }
    if (isBlank(name) || isBlank(email)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "guestName and guestEmail are required");
    }

    Booking booking =
        new Booking(
            hold.getBusinessId(),
            hold.getStaffId(),
            hold.getOfferingId(),
            customerId,
            name.trim(),
            email.trim().toLowerCase(),
            req.guestPhone(),
            hold.getStartsAt(),
            hold.getEndsAt(),
            Duration.between(hold.getBlockedFrom(), hold.getStartsAt()),
            Duration.between(hold.getEndsAt(), hold.getBlockedTo()),
            offering.getPrice(),
            offering.getCurrency(),
            key,
            hold.getId(),
            now);
    bookings.saveAndFlush(booking);
    holds.delete(hold);
    holds.flush();
    events.publishEvent(new BookingCreated(booking.getId(), user == null ? null : user.id()));
    return booking;
  }

  /** An earlier booking made from this hold (or with this key) that a retry should get back. */
  private Optional<Booking> findReplay(UUID holdId, String key) {
    Optional<Booking> byHold = bookings.findByHoldId(holdId);
    if (byHold.isPresent()) {
      Booking existing = byHold.get();
      if (key != null && key.equals(existing.getIdempotencyKey())) {
        return byHold;
      }
      throw new ApiException(ErrorCode.CONFLICT, "This hold has already been used for a booking");
    }
    if (key != null) {
      Optional<Hold> hold = holds.findById(holdId);
      if (hold.isPresent()
          && bookings
              .findByBusinessIdAndIdempotencyKey(hold.get().getBusinessId(), key)
              .isPresent()) {
        throw new ApiException(
            ErrorCode.IDEMPOTENCY_KEY_REUSED,
            "This Idempotency-Key was already used for a different booking");
      }
    }
    return Optional.empty();
  }

  // ---- read ----

  public BookingResponse get(UUID id, Caller caller) {
    return tx.execute(
        status -> {
          Booking booking = load(id);
          access.resolve(booking, caller);
          return views.view(booking, false);
        });
  }

  // ---- reschedule ----

  public BookingResponse reschedule(UUID id, RescheduleRequest req, Caller caller) {
    try {
      Booking moved = tx.execute(status -> doReschedule(id, req, caller));
      return views.view(moved, true);
    } catch (DataIntegrityViolationException e) {
      if (ConstraintViolations.isExclusionViolation(e)) {
        throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "This time slot is no longer available");
      }
      throw e;
    }
  }

  private Booking doReschedule(UUID id, RescheduleRequest req, Caller caller) {
    Booking booking = load(id);
    Capacity capacity = access.resolve(booking, caller);
    if (capacity == Capacity.STAFF) {
      throw ApiException.forbidden("Staff cannot reschedule bookings");
    }
    requireConfirmed(booking);
    Business business = businesses.findById(booking.getBusinessId()).orElseThrow();
    requireWithinCutoff(capacity, booking, business);

    Offering offering = offerings.findById(booking.getOfferingId()).orElseThrow();
    List<UUID> free =
        availability.freeStaffAt(
            business, offering, req.newStart(), req.staffId(), booking.getId());
    UUID target;
    if (free.contains(booking.getStaffId())
        && (req.staffId() == null || req.staffId().equals(booking.getStaffId()))) {
      target = booking.getStaffId();
    } else if (!free.isEmpty()) {
      target = free.getFirst();
    } else {
      throw new ApiException(ErrorCode.SLOT_UNAVAILABLE, "This time slot is not available");
    }

    Instant previousStart = booking.getStartsAt();
    booking.moveTo(
        target,
        req.newStart(),
        req.newStart().plus(offering.duration()),
        Duration.ofMinutes(offering.getBufferBefore()),
        Duration.ofMinutes(offering.getBufferAfter()));
    bookings.saveAndFlush(booking);
    events.publishEvent(new BookingRescheduled(booking.getId(), previousStart, actorId(caller)));
    return booking;
  }

  // ---- cancel ----

  public BookingResponse cancel(UUID id, Caller caller) {
    Booking cancelled = tx.execute(status -> doCancel(id, caller));
    return views.view(cancelled, false);
  }

  private Booking doCancel(UUID id, Caller caller) {
    Booking booking = load(id);
    Capacity capacity = access.resolve(booking, caller);
    if (capacity == Capacity.STAFF) {
      throw ApiException.forbidden("Staff cannot cancel bookings");
    }
    requireConfirmed(booking);
    Business business = businesses.findById(booking.getBusinessId()).orElseThrow();
    requireWithinCutoff(capacity, booking, business);
    booking.cancel(clock.instant());
    bookings.saveAndFlush(booking);
    events.publishEvent(
        new BookingCancelled(booking.getId(), actorId(caller), capacity == Capacity.OWNER));
    return booking;
  }

  // ---- status (staff / owner) ----

  public BookingResponse updateStatus(UUID id, BookingStatus newStatus, AuthUser user) {
    if (newStatus != BookingStatus.COMPLETED && newStatus != BookingStatus.NO_SHOW) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "status must be COMPLETED or NO_SHOW");
    }
    Booking updated =
        tx.execute(
            status -> {
              Booking booking = load(id);
              Capacity capacity = access.resolve(booking, new Caller(user, null));
              if (capacity != Capacity.OWNER && capacity != Capacity.STAFF) {
                throw ApiException.forbidden("Only the owner or assigned staff can set the status");
              }
              if (booking.getStatus() == BookingStatus.CANCELLED) {
                throw new ApiException(
                    ErrorCode.INVALID_STATE, "A cancelled booking cannot change status");
              }
              if (clock.instant().isBefore(booking.getStartsAt())) {
                throw new ApiException(
                    ErrorCode.INVALID_STATE, "The appointment has not started yet");
              }
              booking.setStatus(newStatus);
              bookings.saveAndFlush(booking);
              events.publishEvent(
                  new BookingStatusChanged(booking.getId(), newStatus.name(), user.id()));
              return booking;
            });
    return views.view(updated, false);
  }

  // ---- helpers ----

  private Booking load(UUID id) {
    return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
  }

  private static void requireConfirmed(Booking booking) {
    if (booking.getStatus() != BookingStatus.CONFIRMED) {
      throw new ApiException(
          ErrorCode.INVALID_STATE,
          "Only confirmed bookings can be changed (is " + booking.getStatus() + ")");
    }
  }

  /** Owners may always act; everyone else must be before the business's cutoff. */
  private void requireWithinCutoff(Capacity capacity, Booking booking, Business business) {
    if (capacity == Capacity.OWNER) {
      return;
    }
    Duration cutoff = Duration.ofMinutes(business.getCancelCutoffMinutes());
    if (!CancellationPolicy.isWithinWindow(clock.instant(), booking.getStartsAt(), cutoff)) {
      throw new ApiException(
          ErrorCode.CANCELLATION_WINDOW_CLOSED,
          "Changes are only possible until "
              + business.getCancelCutoffMinutes() / 60.0
              + " hours before the start; please contact the business");
    }
  }

  private static UUID actorId(Caller caller) {
    return caller.user() == null ? null : caller.user().id();
  }

  private static String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    String trimmed = key.trim();
    if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key is too long");
    }
    return trimmed;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
