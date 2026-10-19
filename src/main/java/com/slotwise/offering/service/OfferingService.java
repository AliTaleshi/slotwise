package com.slotwise.offering.service;

import com.slotwise.auth.security.AuthUser;
import com.slotwise.business.service.BusinessAccess;
import com.slotwise.common.error.ApiException;
import com.slotwise.offering.dto.OfferingDtos.CreateOfferingRequest;
import com.slotwise.offering.dto.OfferingDtos.OfferingResponse;
import com.slotwise.offering.dto.OfferingDtos.UpdateOfferingRequest;
import com.slotwise.offering.entity.Offering;
import com.slotwise.offering.mapper.OfferingMapper;
import com.slotwise.offering.repository.OfferingRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferingService {

  private final OfferingRepository offerings;
  private final BusinessAccess access;
  private final OfferingMapper mapper;

  public OfferingService(
      OfferingRepository offerings, BusinessAccess access, OfferingMapper mapper) {
    this.offerings = offerings;
    this.access = access;
    this.mapper = mapper;
  }

  @Transactional
  public OfferingResponse create(UUID businessId, CreateOfferingRequest req, AuthUser owner) {
    access.requireOwned(businessId, owner);
    Offering o =
        new Offering(
            businessId,
            req.name().trim(),
            req.durationMinutes(),
            req.price(),
            req.currency(),
            req.bufferBefore() == null ? 0 : req.bufferBefore(),
            req.bufferAfter() == null ? 0 : req.bufferAfter());
    return mapper.toResponse(offerings.save(o));
  }

  @Transactional(readOnly = true)
  public List<OfferingResponse> list(UUID businessId, AuthUser owner) {
    access.requireOwned(businessId, owner);
    return offerings.findByBusinessIdOrderByNameAsc(businessId).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional
  public OfferingResponse update(
      UUID businessId, UUID id, UpdateOfferingRequest req, AuthUser owner) {
    Offering o = load(businessId, id, owner);
    if (req.name() != null) o.setName(req.name().trim());
    if (req.durationMinutes() != null) o.setDurationMinutes(req.durationMinutes());
    if (req.price() != null) o.setPrice(req.price());
    if (req.currency() != null) o.setCurrency(req.currency());
    if (req.bufferBefore() != null) o.setBufferBefore(req.bufferBefore());
    if (req.bufferAfter() != null) o.setBufferAfter(req.bufferAfter());
    if (req.active() != null) o.setActive(req.active());
    return mapper.toResponse(o);
  }

  /** Soft delete: bookings keep referencing the service, so it is only deactivated. */
  @Transactional
  public void deactivate(UUID businessId, UUID id, AuthUser owner) {
    load(businessId, id, owner).setActive(false);
  }

  private Offering load(UUID businessId, UUID id, AuthUser owner) {
    access.requireOwned(businessId, owner);
    Offering o = offerings.findById(id).orElseThrow(() -> ApiException.notFound("Service"));
    if (!o.getBusinessId().equals(businessId)) {
      throw ApiException.notFound("Service");
    }
    return o;
  }
}
