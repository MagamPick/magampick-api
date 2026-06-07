package com.magampick.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 소비자 환불 요청 Request DTO. */
@Schema(description = "환불 요청")
public record RefundRequestRequest(
    @Schema(description = "환불 사유 (1~200자)", example = "상품이 예상과 달랐어요")
        @NotBlank(message = "환불 사유를 입력해 주세요")
        @Size(min = 1, max = 200, message = "환불 사유는 1~200자 이내로 입력해 주세요")
        String reason) {}
