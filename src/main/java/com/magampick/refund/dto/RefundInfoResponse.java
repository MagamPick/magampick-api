package com.magampick.refund.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** OrderResponse 내부 환불 sub-field. 환불 미요청이면 null. */
@Schema(description = "환불 요약 정보 (환불 미요청 시 null)")
public record RefundInfoResponse(
    @Schema(description = "환불 상태", example = "REQUESTED") String status,
    @Schema(description = "환불 사유", example = "상품이 예상과 달랐어요") String reason,
    @Schema(description = "환불 요청 시각 (KST ISO 8601)") OffsetDateTime requestedAt,
    @Schema(description = "거부 사유 (nullable)") @JsonInclude(JsonInclude.Include.NON_NULL)
        String rejectReason,
    @Schema(description = "처리 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime resolvedAt) {}
