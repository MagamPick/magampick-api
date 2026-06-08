package com.magampick.point.mapper;

import com.magampick.point.domain.PointTransaction;
import com.magampick.point.dto.PointTransactionResponse;
import java.util.List;
import org.mapstruct.Mapper;

/** PointTransaction 엔티티 → DTO 변환. */
@Mapper(componentModel = "spring")
public interface PointTransactionMapper {

  PointTransactionResponse toResponse(PointTransaction tx);

  List<PointTransactionResponse> toResponseList(List<PointTransaction> txs);
}
