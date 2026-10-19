package com.slotwise.booking.service;

import com.slotwise.auth.entity.Role;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.booking.entity.Booking;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.common.error.ApiException;
import com.slotwise.staff.repository.StaffRepository;
import org.springframework.stereotype.Component;

/** Decides how (if at all) the caller may act on a given booking. */
@Component
public class BookingAccess {

  /** The capacity in which the caller may act on the booking. */
  public enum Capacity {
    OWNER,
    STAFF,
    CUSTOMER,
    GUEST_LINK
  }

  /** Who is calling: an authenticated user and/or the holder of a manage-link token. */
  public record Caller(AuthUser user, String manageToken) {}

  private final BusinessRepository businesses;
  private final StaffRepository staff;
  private final ManageTokenService manageTokens;

  public BookingAccess(
      BusinessRepository businesses, StaffRepository staff, ManageTokenService manageTokens) {
    this.businesses = businesses;
    this.staff = staff;
    this.manageTokens = manageTokens;
  }

  public Capacity resolve(Booking booking, Caller caller) {
    AuthUser user = caller.user();
    if (user != null) {
      if (user.role() == Role.OWNER
          && businesses
              .findById(booking.getBusinessId())
              .map(b -> b.getOwnerId().equals(user.id()))
              .orElse(false)) {
        return Capacity.OWNER;
      }
      if (user.role() == Role.STAFF
          && staff
              .findById(booking.getStaffId())
              .map(s -> user.id().equals(s.getUserId()))
              .orElse(false)) {
        return Capacity.STAFF;
      }
      if (user.role() == Role.CUSTOMER && user.id().equals(booking.getCustomerId())) {
        return Capacity.CUSTOMER;
      }
    }
    if (manageTokens.isValidFor(caller.manageToken(), booking.getId())) {
      return Capacity.GUEST_LINK;
    }
    throw ApiException.forbidden("You do not have access to this booking");
  }
}
