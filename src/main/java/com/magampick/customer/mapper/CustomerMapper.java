package com.magampick.customer.mapper;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.dto.CustomerProfileResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

  @Mapping(target = "phoneVerifiedAt", source = "phoneVerifiedAt", qualifiedByName = "toKst")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  CustomerProfileResponse toProfileResponse(Customer customer);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }
}
