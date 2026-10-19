package com.slotwise.business.service;

import com.slotwise.audit.service.AuditService;
import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.dto.BusinessDtos.BusinessResponse;
import com.slotwise.business.dto.BusinessDtos.CreateBusinessRequest;
import com.slotwise.business.dto.BusinessDtos.PublicBusinessResponse;
import com.slotwise.business.dto.BusinessDtos.UpdateBusinessRequest;
import com.slotwise.business.entity.Business;
import com.slotwise.business.mapper.BusinessMapper;
import com.slotwise.business.repository.BusinessRepository;
import com.slotwise.common.error.ApiException;
import com.slotwise.common.error.ErrorCode;
import com.slotwise.offering.mapper.OfferingMapper;
import com.slotwise.offering.repository.OfferingRepository;
import com.slotwise.staff.mapper.StaffMapper;
import com.slotwise.staff.repository.StaffRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {

  private final BusinessRepository businesses;
  private final StaffRepository staff;
  private final OfferingRepository offerings;
  private final BusinessAccess access;
  private final BusinessMapper mapper;
  private final StaffMapper staffMapper;
  private final OfferingMapper offeringMapper;
  private final Clock clock;
  private final AuditService audit;

  public BusinessService(
      BusinessRepository businesses,
      StaffRepository staff,
      OfferingRepository offerings,
      BusinessAccess access,
      BusinessMapper mapper,
      StaffMapper staffMapper,
      OfferingMapper offeringMapper,
      Clock clock,
      AuditService audit) {
    this.businesses = businesses;
    this.staff = staff;
    this.offerings = offerings;
    this.access = access;
    this.mapper = mapper;
    this.staffMapper = staffMapper;
    this.offeringMapper = offeringMapper;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional
  public BusinessResponse create(CreateBusinessRequest req, AuthUser owner) {
    if (businesses.existsBySlug(req.slug())) {
      throw new ApiException(ErrorCode.SLUG_TAKEN, "Slug is already in use");
    }
    Business b =
        new Business(owner.id(), req.name().trim(), req.slug(), req.timeZone(), clock.instant());
    b.setContactEmail(req.contactEmail());
    b.setContactPhone(req.contactPhone());
    if (req.minNoticeMinutes() != null) b.setMinNoticeMinutes(req.minNoticeMinutes());
    if (req.maxAdvanceDays() != null) b.setMaxAdvanceDays(req.maxAdvanceDays());
    if (req.cancelCutoffMinutes() != null) b.setCancelCutoffMinutes(req.cancelCutoffMinutes());
    if (req.slotIntervalMinutes() != null)
      b.setSlotIntervalMinutes(checkInterval(req.slotIntervalMinutes()));
    try {
      return mapper.toResponse(businesses.saveAndFlush(b));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ErrorCode.SLUG_TAKEN, "Slug is already in use");
    }
  }

  @Transactional(readOnly = true)
  public List<BusinessResponse> listOwned(AuthUser owner) {
    return businesses.findByOwnerIdOrderByCreatedAtAsc(owner.id()).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public BusinessResponse get(UUID id, AuthUser owner) {
    return mapper.toResponse(access.requireOwned(id, owner));
  }

  @Transactional
  public BusinessResponse update(UUID id, UpdateBusinessRequest req, AuthUser owner) {
    Business b = access.requireOwned(id, owner);
    BusinessResponse before = mapper.toResponse(b);
    if (req.name() != null) b.setName(req.name().trim());
    if (req.timeZone() != null) b.setTimeZone(req.timeZone());
    if (req.contactEmail() != null) b.setContactEmail(req.contactEmail());
    if (req.contactPhone() != null) b.setContactPhone(req.contactPhone());
    if (req.minNoticeMinutes() != null) b.setMinNoticeMinutes(req.minNoticeMinutes());
    if (req.maxAdvanceDays() != null) b.setMaxAdvanceDays(req.maxAdvanceDays());
    if (req.cancelCutoffMinutes() != null) b.setCancelCutoffMinutes(req.cancelCutoffMinutes());
    if (req.slotIntervalMinutes() != null)
      b.setSlotIntervalMinutes(checkInterval(req.slotIntervalMinutes()));
    BusinessResponse after = mapper.toResponse(b);
    audit.record(
        owner.id(), b.getId(), "BUSINESS_SETTINGS_CHANGED", "BUSINESS", b.getId(), before, after);
    return after;
  }

  @Transactional(readOnly = true)
  public PublicBusinessResponse publicProfile(String slug) {
    Business b = businesses.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Business"));
    return new PublicBusinessResponse(
        b.getName(),
        b.getSlug(),
        b.getTimeZone(),
        b.getContactEmail(),
        b.getContactPhone(),
        b.getMinNoticeMinutes(),
        b.getMaxAdvanceDays(),
        b.getCancelCutoffMinutes(),
        b.getSlotIntervalMinutes(),
        offerings.findByBusinessIdAndActiveTrueOrderByNameAsc(b.getId()).stream()
            .map(offeringMapper::toPublic)
            .toList(),
        staff.findByBusinessIdAndActiveTrueOrderByNameAsc(b.getId()).stream()
            .map(staffMapper::toPublic)
            .toList());
  }

  /** Slots must tile the hour so that every day has the same grid. */
  private static int checkInterval(int minutes) {
    if (60 % minutes != 0) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "slotIntervalMinutes must divide 60 (5, 10, 15, 20, 30, 60)");
    }
    return minutes;
  }
}
