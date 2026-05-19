package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "매장 등록 반려 요청")
public record StoreRejectRequest(
    @Schema(description = "반려 사유", example = "사업자번호가 유효하지 않습니다") @NotBlank @Size(max = 500)
        String rejectionReason) {}
