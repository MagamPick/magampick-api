package com.magampick.clearance.mapper;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.dto.ClearanceItemResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ClearanceItemMapper {

  @Mapping(target = "productId", source = "product.id")
  @Mapping(target = "imageUrl", source = "product.imageUrl")
  @Mapping(target = "discountRate", source = "discountRate")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  @Mapping(target = "pickupStartAt", source = "pickupStartAt", qualifiedByName = "toKst")
  @Mapping(target = "pickupEndAt", source = "pickupEndAt", qualifiedByName = "toKst")
  @Mapping(target = "closeReason", source = "closeReason")
  ClearanceItemResponse toResponse(ClearanceItem item);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }
}
