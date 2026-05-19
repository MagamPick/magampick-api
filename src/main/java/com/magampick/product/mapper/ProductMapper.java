package com.magampick.product.mapper;

import com.magampick.product.domain.Product;
import com.magampick.product.dto.ProductResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ProductMapper {

  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  ProductResponse toResponse(Product product);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }
}
