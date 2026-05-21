package com.magampick.favorite.mapper;

import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteStoreResponse;
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

  @Mapping(target = "storeId", source = "store.id")
  @Mapping(target = "storeName", source = "store.name")
  @Mapping(target = "roadAddress", source = "store.roadAddress")
  @Mapping(target = "imageUrl", source = "store.imageUrl")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  FavoriteStoreResponse toStoreResponse(Favorite favorite);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }
}
