package com.magampick.favorite.mapper;

import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface FavoriteMapper {

  @Mapping(target = "storeId", source = "store.id")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  FavoriteAddResponse toAddResponse(Favorite favorite);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }
}
