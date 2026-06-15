package com.magampick.address.mapper;

import com.magampick.address.domain.Address;
import com.magampick.address.dto.AddressResponse;
import com.magampick.global.common.GeometryUtil;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface AddressMapper {

  @Mapping(target = "latitude", expression = "java(toLatitude(address))")
  @Mapping(target = "longitude", expression = "java(toLongitude(address))")
  @Mapping(target = "isDefault", expression = "java(address.isDefault())")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "toKst")
  AddressResponse toResponse(Address address);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }

  default Double toLatitude(Address address) {
    return address == null ? null : GeometryUtil.latitude(address.getLocation());
  }

  default Double toLongitude(Address address) {
    return address == null ? null : GeometryUtil.longitude(address.getLocation());
  }
}
