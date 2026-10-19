package com.slotwise.business.mapper;

import com.slotwise.business.dto.BusinessDtos.BusinessResponse;
import com.slotwise.business.entity.Business;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BusinessMapper {

  BusinessResponse toResponse(Business business);
}
