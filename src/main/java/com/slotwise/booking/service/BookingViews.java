package com.slotwise.booking.service;

import com.slotwise.booking.dto.BookingDtos.BookingResponse;
import com.slotwise.booking.entity.Booking;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.offering.repository.OfferingRepository;
import com.slotwise.staff.repository.StaffRepository;
import org.springframework.stereotype.Component;

/** Builds {@link BookingResponse}s: names, business-zone times and optional manage link. */
@Component
public class BookingViews {

  private final BusinessRepository businesses;
  private final StaffRepository staff;
  private final OfferingRepository offerings;
  private final ManageTokenService manageTokens;
  private final BookingProperties props;

  public BookingViews(
      BusinessRepository businesses,
      StaffRepository staff,
      OfferingRepository offerings,
      ManageTokenService manageTokens,
      BookingProperties props) {
    this.businesses = businesses;
    this.staff = staff;
    this.offerings = offerings;
    this.manageTokens = manageTokens;
    this.props = props;
  }

  public BookingResponse view(Booking b, boolean includeManageLink) {
    Business business = businesses.findById(b.getBusinessId()).orElseThrow();
    String token = includeManageLink ? manageTokens.issue(b) : null;
    return new BookingResponse(
        b.getId(),
        b.getBusinessId(),
        b.getStatus(),
        b.getStaffId(),
        staff.findById(b.getStaffId()).map(s -> s.getName()).orElse(null),
        b.getOfferingId(),
        offerings.findById(b.getOfferingId()).map(o -> o.getName()).orElse(null),
        business.getTimeZone(),
        b.getStartsAt().atZone(business.zone()).toOffsetDateTime(),
        b.getEndsAt().atZone(business.zone()).toOffsetDateTime(),
        b.getPrice(),
        b.getCurrency(),
        b.getGuestName(),
        b.getGuestEmail(),
        b.getGuestPhone(),
        b.getCustomerId(),
        b.getVersion(),
        b.getCreatedAt(),
        token,
        token == null ? null : manageUrl(b, token));
  }

  public String manageUrl(Booking b, String token) {
    return props.publicBaseUrl() + "/v1/bookings/" + b.getId() + "?token=" + token;
  }
}
