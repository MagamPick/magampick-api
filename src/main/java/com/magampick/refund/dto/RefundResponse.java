package com.magampick.refund.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 사장용 환불 요청 뷰. FE RefundRequest 타입 기준. */
@Schema(description = "환불 요청 (사장 뷰)")
public record RefundResponse(
    @Schema(description = "환불 ID", example = "1") Long id,
    @Schema(description = "주문 ID", example = "42") Long orderId,
    @Schema(description = "표시용 주문 번호", example = "0042") String orderNo,
    @Schema(description = "매장 ID", example = "1") Long storeId,
    @Schema(description = "고객 닉네임", example = "테스터") String customerName,
    @Schema(description = "환불 주문 항목 목록") List<RefundItemResponse> items,
    @Schema(description = "주문 결제액", example = "6000") BigDecimal amount,
    @Schema(description = "수령완료 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime pickupCompletedAt,
    @Schema(description = "환불 상태", example = "REQUESTED") String status,
    @Schema(description = "환불 사유", example = "상품이 예상과 달랐어요") String reason,
    @Schema(description = "환불 요청 시각 (KST ISO 8601)") OffsetDateTime requestedAt,
    @Schema(description = "거부 사유 (nullable)") @JsonInclude(JsonInclude.Include.NON_NULL)
        String rejectReason,
    @Schema(description = "처리 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime resolvedAt) {}
