package com.magampick.store.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매장 등록 응답")
public record StoreRegisterResponse(
    @Schema(description = "생성된 매장 ID", example = "1") Long storeId,
    @Schema(description = "초기 영업 상태 (등록 직후 CLOSED_TODAY)", example = "CLOSED_TODAY")
        OperationStatus operationStatus) {}
