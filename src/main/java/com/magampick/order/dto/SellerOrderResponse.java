package com.magampick.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** 사장 주문 응답. 소비자 OrderResponse 필드 + 고객 정보 + 4노드 타임라인. */
@Schema(description = "사장 주문 응답")
public record SellerOrderResponse(
    @Schema(description = "주문 ID", example = "42") Long id,
    @Schema(description = "표시용 주문 번호 (id 기반 파생)", example = "0042") String orderNo,
    @Schema(description = "매장 ID", example = "1") Long storeId,
    @Schema(description = "매장명", example = "동네빵집") String storeName,
    @Schema(description = "매장 전화번호 (nullable)", example = "0212345678")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String storePhone,
    @Schema(description = "주문 항목 목록") List<OrderResponse.OrderItemResponse> items,
    @Schema(description = "픽업 정보") OrderResponse.PickupResponse pickup,
    @Schema(description = "픽업 요청 메모") @JsonInclude(JsonInclude.Include.NON_NULL) String memo,
    @Schema(description = "금액 요약") OrderResponse.OrderAmountsResponse amounts,
    @Schema(description = "픽업 인증 코드 4자리", example = "3827") String pickupCode,
    @Schema(description = "주문 상태", example = "PENDING") String status,
    @Schema(description = "결제 수단", example = "toss") String paymentMethod,
    @Schema(description = "생성 시각 (KST ISO 8601)") OffsetDateTime createdAt,
    @Schema(description = "고객 이름", example = "홍길동") String customerName,
    @Schema(description = "고객 전화번호 (nullable)", example = "01012345678")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String customerPhone,
    @Schema(description = "수락 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime acceptedAt,
    @Schema(description = "준비완료 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime readyAt,
    @Schema(description = "수령완료 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime completedAt,
    @Schema(description = "거절 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime rejectedAt,
    @Schema(description = "취소 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime cancelledAt) {}
