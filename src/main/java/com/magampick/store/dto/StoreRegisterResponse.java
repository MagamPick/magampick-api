package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매장 등록 신청 응답")
public record StoreRegisterResponse(
    @Schema(description = "생성된 매장 ID", example = "1") Long storeId) {}
