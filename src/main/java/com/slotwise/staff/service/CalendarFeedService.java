package com.slotwise.staff.service;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.auth.security.JwtProperties;
import com.slotwise.booking.entity.BookingStatus;
import com.slotwise.booking.repository.BookingRepository;
import com.slotwise.booking.service.BookingProperties;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.error.ApiException;
import com.slotwise.notification.service.IcsBuilder;
import com.slotwise.offering.entity.Offering;
import com.slotwise.offering.repository.OfferingRepository;
import com.slotwise.staff.entity.Staff;
import com.slotwise.staff.repository.StaffRepository;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * iCal subscription feed per staff member. Calendar apps cannot send headers, so the feed URL
 * carries an unguessable token (HMAC of the staff id) instead of a login. Tokens do not expire, so
 * the subscription keeps working.
 */
@Service
public class CalendarFeedService {

  private static final Duration LOOK_BACK = Duration.ofDays(30);
  private static final Duration LOOK_AHEAD = Duration.ofDays(365);

  private final StaffRepository staff;
  private final BusinessRepository businesses;
  private final OfferingRepository offerings;
  private final BookingRepository bookings;
  private final BusinessAccess access;
  private final BookingProperties props;
  private final Clock clock;
  private final byte[] key;

  public CalendarFeedService(
      StaffRepository staff,
      BusinessRepository businesses,
      OfferingRepository offerings,
      BookingRepository bookings,
      BusinessAccess access,
      BookingProperties props,
      JwtProperties jwt,
      Clock clock) {
    this.staff = staff;
    this.businesses = businesses;
    this.offerings = offerings;
    this.bookings = bookings;
    this.access = access;
    this.props = props;
    this.clock = clock;
    this.key = sha256("staff-calendar:" + jwt.secret());
  }

  /** The subscription URL for a staff member; only the owner or that staff member may ask. */
  @Transactional(readOnly = true)
  public String linkFor(UUID staffId, AuthUser user) {
    access.requireStaffOwnerOrSelf(staffId, user);
    return props.publicBaseUrl() + "/v1/staff/" + staffId + "/calendar.ics?token=" + token(staffId);
  }

  @Transactional(readOnly = true)
  public String feed(UUID staffId, String token) {
    if (token == null
        || !MessageDigest.isEqual(
            token(staffId).getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8))) {
      throw ApiException.forbidden("Invalid calendar token");
    }
    Staff member = staff.findById(staffId).orElseThrow(() -> ApiException.notFound("Staff member"));
    Business business = businesses.findById(member.getBusinessId()).orElseThrow();
    Instant now = clock.instant();
    Map<UUID, Offering> services = new HashMap<>();
    List<IcsBuilder.Event> events =
        bookings
            .findByStaffIdAndStatusNotAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                staffId, BookingStatus.CANCELLED, now.minus(LOOK_BACK), now.plus(LOOK_AHEAD))
            .stream()
            .map(
                b -> {
                  Offering o =
                      services.computeIfAbsent(
                          b.getOfferingId(), id -> offerings.findById(id).orElseThrow());
                  return new IcsBuilder.Event(
                      b.getId(),
                      b.getStartsAt(),
                      b.getEndsAt(),
                      o.getName() + " – " + b.getGuestName(),
                      "Guest: " + b.getGuestName() + " <" + b.getGuestEmail() + ">",
                      business.getName(),
                      false,
                      (int) b.getVersion());
                })
            .toList();
    return IcsBuilder.calendar(events, null, member.getName() + " – " + business.getName(), now);
  }

  private String token(UUID staffId) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(staffId.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
