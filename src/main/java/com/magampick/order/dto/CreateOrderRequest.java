package com.magampick.order.dto;

import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.PickupType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 주문 생성 요청. 단일 매장, 최소 1개 항목. paymentAgreed=false 시 거부. amounts 는 교차검증용(선택). */
@Schema(description = "주문 생성 요청")
public record CreateOrderRequest(
    @Schema(description = "매장 ID", example = "1") @NotNull Long storeId,
    @Schema(description = "주문 항목 목록 (최소 1개)") @NotEmpty @Valid List<OrderItemRequest> items,
    @Schema(description = "픽업 정보") @NotNull @Valid PickupRequest pickup,
    @Schema(description = "픽업 요청 메모 (≤80자, 선택)", example = "빵 나오는 즉시 픽업 예정입니다") @Size(max = 80)
        String memo,
    @Schema(description = "결제 수단 (toss 고정)", example = "toss")
        @NotBlank
        @Pattern(regexp = "toss", message = "결제 수단은 toss만 지원합니다")
        String paymentMethod,
    @Schema(description = "결제 동의 여부 (true 필수)", example = "true") @NotNull Boolean paymentAgreed,
    @Schema(description = "금액 교차검증 (선택 — 불일치 시 AMOUNT_MISMATCH)") @Valid AmountsRequest amounts) {

  @Schema(description = "주문 항목")
  public record OrderItemRequest(
      @Schema(description = "상품 종류 (DEAL=떨이, MENU=일반)", example = "DEAL") @NotNull ItemKind kind,
      @Schema(description = "상품 ID (DEAL=clearanceItemId, MENU=productId)", example = "10") @NotNull
          Long refId,
      @Schema(description = "수량 (1~10)", example = "2") @Min(1) @Max(10) int quantity) {}

  @Schema(description = "픽업 요청")
  public record PickupRequest(
      @Schema(description = "픽업 유형 (ASAP=즉시, SLOT=시간 지정)", example = "ASAP") @NotNull
          PickupType type,
      @Schema(description = "픽업 시각 (SLOT 시 HH:mm, 15분 단위, 영업종료 전)", example = "18:30")
          String time) {}

  @Schema(description = "금액 교차검증 요청 (선택)")
  public record AmountsRequest(
      @Schema(description = "정상가 합계", example = "9000") @NotNull BigDecimal normalTotal,
      @Schema(description = "할인 합계 (떨이 할인분)", example = "3000") @NotNull BigDecimal discountTotal,
      @Schema(description = "결제액 = normalTotal - discountTotal", example = "6000") @NotNull
          BigDecimal payTotal) {}
}
