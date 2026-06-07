package com.magampick.settlement.mapper;

import com.magampick.settlement.domain.Settlement;
import com.magampick.settlement.dto.SettlementCycleResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Settlement 엔티티 → DTO 변환. periodLabel 등 파생 필드는 서비스에서 처리. */
@Mapper(componentModel = "spring")
public interface SettlementMapper {

  @Mapping(target = "storeId", source = "store.id")
  @Mapping(target = "periodStart", source = "periodStart", qualifiedByName = "localDateToKst")
  @Mapping(target = "periodEnd", source = "periodEnd", qualifiedByName = "localDateToKst")
  @Mapping(target = "depositDate", source = "depositDate", qualifiedByName = "localDateToKst")
  @Mapping(target = "status", source = "status")
  SettlementCycleResponse toCycleResponse(Settlement settlement);

  /** LocalDate → 해당 일 00:00:00 KST(+09:00) OffsetDateTime. */
  @Named("localDateToKst")
  default OffsetDateTime localDateToKst(LocalDate date) {
    if (date == null) return null;
    return date.atStartOfDay().atOffset(ZoneOffset.ofHours(9));
  }
}
