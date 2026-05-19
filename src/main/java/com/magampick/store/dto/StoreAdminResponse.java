package com.magampick.store.dto;

import com.magampick.store.domain.StoreStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "관리자 매장 목록 응답")
public record StoreAdminResponse(
    @Schema(description = "매장 ID", example = "1") Long id,
    @Schema(description = "매장명", example = "동네빵집") String name,
    @Schema(description = "도로명 주소") String roadAddress,
    @Schema(description = "매장 상태") StoreStatus status,
    @Schema(description = "사장 ID") Long sellerId,
    @Schema(description = "사장 이름") String sellerOwnerName,
    @Schema(description = "등록 신청 시각") OffsetDateTime createdAt) {}
