package com.magampick.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.magampick.refund.dto.RefundInfoResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 주문 응답. FE orderSchema 필드명 기준, enum 값은 대문자(BE 컨벤션). */
@Schema(description = "주문 응답")
public record OrderResponse(
    @Schema(description = "주문 ID", example = "42") Long id,
    @Schema(description = "표시용 주문 번호 (id 기반 파생)", example = "0042") String orderNo,
    @Schema(description = "매장 ID", example = "1") Long storeId,
    @Schema(description = "매장명", example = "동네빵집") String storeName,
    @Schema(description = "매장 전화번호 (nullable)", example = "0212345678")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String storePhone,
    @Schema(description = "주문 항목 목록") List<OrderItemResponse> items,
    @Schema(description = "픽업 정보") PickupResponse pickup,
    @Schema(description = "픽업 요청 메모") @JsonInclude(JsonInclude.Include.NON_NULL) String memo,
    @Schema(description = "금액 요약") OrderAmountsResponse amounts,
    @Schema(description = "픽업 인증 코드 4자리", example = "3827") String pickupCode,
    @Schema(description = "주문 상태", example = "PENDING") String status,
    @Schema(description = "결제 수단", example = "toss") String paymentMethod,
    @Schema(description = "생성 시각 (KST ISO 8601)") OffsetDateTime createdAt,
    @Schema(description = "수령완료 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime completedAt,
    @Schema(description = "취소 시각 (KST ISO 8601, nullable)")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime cancelledAt,
    @Schema(description = "환불 정보 (환불 미요청 시 null)") @JsonInclude(JsonInclude.Include.NON_NULL)
        RefundInfoResponse refund) {

  @Schema(description = "주문 항목")
  public record OrderItemResponse(
      @Schema(description = "항목 ID") Long id,
      @Schema(description = "상품 종류 (DEAL/MENU)", example = "DEAL") String kind,
      @Schema(description = "상품명", example = "크로아상") String name,
      @Schema(description = "이미지 URL (nullable)") @JsonInclude(JsonInclude.Include.NON_NULL)
          String imageUrl,
      @Schema(description = "정상가", example = "4500") BigDecimal originalPrice,
      @Schema(description = "결제 단가 (DEAL=할인가, MENU=정상가)", example = "3000") BigDecimal salePrice,
      @Schema(description = "수량", example = "2") int qty) {}

  @Schema(description = "픽업 정보")
  public record PickupResponse(
      @Schema(description = "픽업 유형 (ASAP/SLOT)", example = "ASAP") String type,
      @Schema(description = "픽업 시각 HH:mm (SLOT 시)", example = "18:30")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          String time) {}

  @Schema(description = "금액 요약")
  public record OrderAmountsResponse(
      @Schema(description = "정상가 합계", example = "9000") BigDecimal normalTotal,
      @Schema(description = "할인 합계", example = "3000") BigDecimal discountTotal,
      @Schema(description = "결제액", example = "6000") BigDecimal payTotal) {}
}
