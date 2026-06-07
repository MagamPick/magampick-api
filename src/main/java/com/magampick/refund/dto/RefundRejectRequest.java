package com.magampick.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사장 환불 거부 Request DTO. */
@Schema(description = "환불 거부")
public record RefundRejectRequest(
    @Schema(description = "거부 사유 (1~200자)", example = "픽업 완료 후 24시간이 경과했습니다")
        @NotBlank(message = "거부 사유를 입력해 주세요")
        @Size(min = 1, max = 200, message = "거부 사유는 1~200자 이내로 입력해 주세요")
        String rejectReason) {}
