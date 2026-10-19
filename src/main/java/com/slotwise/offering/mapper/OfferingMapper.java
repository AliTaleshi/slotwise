package com.slotwise.offering.mapper;

import com.slotwise.business.dto.BusinessDtos.PublicService;
import com.slotwise.offering.dto.OfferingDtos.OfferingResponse;
import com.slotwise.offering.entity.Offering;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OfferingMapper {

  OfferingResponse toResponse(Offering offering);

  PublicService toPublic(Offering offering);
}
