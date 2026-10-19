package com.slotwise.business.service;

import com.slotwise.auth.entity.Role;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.entity.Business;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.common.error.ApiException;
import com.slotwise.staff.entity.Staff;
import com.slotwise.staff.repository.StaffRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Central tenant-isolation checks: who may touch which business, staff member or booking. */
@Component
public class BusinessAccess {

  private final BusinessRepository businesses;
  private final StaffRepository staff;

  public BusinessAccess(BusinessRepository businesses, StaffRepository staff) {
    this.businesses = businesses;
    this.staff = staff;
  }

  /** The business, provided {@code user} is its owner. */
  public Business requireOwned(UUID businessId, AuthUser user) {
    Business business =
        businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business"));
    if (user.role() != Role.OWNER || !business.getOwnerId().equals(user.id())) {
      throw ApiException.forbidden("You do not own this business");
    }
    return business;
  }

  /** The staff member, provided {@code user} owns the staff member's business. */
  public Staff requireStaffOfOwner(UUID staffId, AuthUser user) {
    Staff member = staff.findById(staffId).orElseThrow(() -> ApiException.notFound("Staff member"));
    requireOwned(member.getBusinessId(), user);
    return member;
  }

  /** The staff member, provided {@code user} owns the business or is that staff member. */
  public Staff requireStaffOwnerOrSelf(UUID staffId, AuthUser user) {
    Staff member = staff.findById(staffId).orElseThrow(() -> ApiException.notFound("Staff member"));
    boolean self = user.role() == Role.STAFF && user.id().equals(member.getUserId());
    if (!self) {
      requireOwned(member.getBusinessId(), user);
    }
    return member;
  }
}
