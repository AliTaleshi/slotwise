package com.slotwise.staff.service;

import com.slotwise.auth.entity.Role;
import com.slotwise.auth.entity.User;
import com.slotwise.auth.repository.UserRepository;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.offering.entity.Offering;
import com.slotwise.offering.repository.OfferingRepository;
import com.slotwise.staff.dto.StaffDtos.CreateStaffRequest;
import com.slotwise.staff.dto.StaffDtos.StaffResponse;
import com.slotwise.staff.dto.StaffDtos.UpdateStaffRequest;
import com.slotwise.staff.entity.Staff;
import com.slotwise.staff.mapper.StaffMapper;
import com.slotwise.staff.repository.StaffRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

  private final StaffRepository staff;
  private final OfferingRepository offerings;
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final BusinessAccess access;
  private final StaffMapper mapper;
  private final Clock clock;

  public StaffService(
      StaffRepository staff,
      OfferingRepository offerings,
      UserRepository users,
      PasswordEncoder encoder,
      BusinessAccess access,
      StaffMapper mapper,
      Clock clock) {
    this.staff = staff;
    this.offerings = offerings;
    this.users = users;
    this.encoder = encoder;
    this.access = access;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public StaffResponse create(UUID businessId, CreateStaffRequest req, AuthUser owner) {
    access.requireOwned(businessId, owner);
    Set<UUID> serviceIds = checkServices(businessId, req.serviceIds());

    UUID userId = null;
    if (req.password() != null) {
      if (req.email() == null || req.email().isBlank()) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "email is required to create a login");
      }
      String email = req.email().trim().toLowerCase();
      if (users.existsByEmail(email)) {
        throw new ApiException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
      }
      userId =
          users
              .save(
                  new User(
                      email,
                      encoder.encode(req.password()),
                      req.name().trim(),
                      Role.STAFF,
                      clock.instant()))
              .getId();
    }
    Staff member = new Staff(businessId, userId, req.name().trim(), req.email());
    member.getOfferingIds().addAll(serviceIds);
    return mapper.toResponse(staff.save(member));
  }

  @Transactional(readOnly = true)
  public List<StaffResponse> list(UUID businessId, AuthUser owner) {
    access.requireOwned(businessId, owner);
    return staff.findByBusinessIdOrderByNameAsc(businessId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public StaffResponse update(UUID businessId, UUID id, UpdateStaffRequest req, AuthUser owner) {
    Staff member = load(businessId, id, owner);
    if (req.name() != null) member.setName(req.name().trim());
    if (req.email() != null) member.setEmail(req.email());
    if (req.active() != null) member.setActive(req.active());
    if (req.serviceIds() != null) {
      Set<UUID> ids = checkServices(businessId, req.serviceIds());
      member.getOfferingIds().retainAll(ids);
      member.getOfferingIds().addAll(ids);
    }
    return mapper.toResponse(member);
  }

  /** Soft delete: bookings keep referencing the staff member, so they are only deactivated. */
  @Transactional
  public void deactivate(UUID businessId, UUID id, AuthUser owner) {
    load(businessId, id, owner).setActive(false);
  }

  private Staff load(UUID businessId, UUID id, AuthUser owner) {
    access.requireOwned(businessId, owner);
    Staff member = staff.findById(id).orElseThrow(() -> ApiException.notFound("Staff member"));
    if (!member.getBusinessId().equals(businessId)) {
      throw ApiException.notFound("Staff member");
    }
    return member;
  }

  /** Every assigned service must exist in the same business. */
  private Set<UUID> checkServices(UUID businessId, Set<UUID> requested) {
    if (requested == null || requested.isEmpty()) {
      return Set.of();
    }
    Set<UUID> found =
        offerings.findByIdInAndBusinessId(requested, businessId).stream()
            .map(Offering::getId)
            .collect(java.util.stream.Collectors.toSet());
    if (!found.containsAll(requested)) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "serviceIds contains a service from another business");
    }
    return found;
  }
}
