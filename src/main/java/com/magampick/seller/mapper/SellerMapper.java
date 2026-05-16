package com.magampick.seller.mapper;

import com.magampick.seller.domain.Seller;
import com.magampick.seller.dto.SellerProfileResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface SellerMapper {

  @Mapping(target = "phoneVerifiedAt", source = "phoneVerifiedAt", qualifiedByName = "toKst")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  SellerProfileResponse toProfileResponse(Seller seller);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }
}
