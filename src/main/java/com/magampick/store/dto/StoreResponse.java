package com.magampick.store.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "사장 매장 목록 응답")
public record StoreResponse(
    @Schema(description = "매장 ID", example = "1") Long id,
    @Schema(description = "매장명", example = "동네빵집") String name,
    @Schema(description = "도로명 주소") String roadAddress,
    @Schema(description = "상세 주소") String detailAddress,
    @Schema(description = "매장 전화번호", example = "0212345678") String phone,
    @Schema(description = "대표 사진 URL") String imageUrl,
    @Schema(description = "영업 상태") OperationStatus operationStatus,
    @Schema(description = "등록 시각") OffsetDateTime createdAt) {}
