package com.magampick.store.mapper;

import com.magampick.global.common.GeometryUtil;
import com.magampick.store.domain.Store;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface StoreMapper {

  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  StoreResponse toResponse(Store store);

  @Mapping(target = "latitude", expression = "java(toLatitude(store))")
  @Mapping(target = "longitude", expression = "java(toLongitude(store))")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  StoreDetailResponse toDetailResponse(Store store);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }

  default Double toLatitude(Store store) {
    return store == null ? null : GeometryUtil.latitude(store.getLocation());
  }

  default Double toLongitude(Store store) {
    return store == null ? null : GeometryUtil.longitude(store.getLocation());
  }
}
