package com.magampick.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 주문 준비 응답 — POST /orders 결과. 클라이언트는 tossOrderId·amount·orderName 을 토스 SDK에 전달. */
@Schema(description = "주문 준비 응답 (토스 결제 전)")
public record PrepareOrderResponse(
    @Schema(description = "주문 DB ID (confirm 엔드포인트에 사용)", example = "42") Long orderId,
    @Schema(description = "토스 SDK에 전달할 주문 ID (6자 이상, 'order-{orderId}' 형식)", example = "order-42")
        String tossOrderId,
    @Schema(description = "결제 금액", example = "6000") BigDecimal amount,
    @Schema(description = "주문명 (토스 SDK 표시용)", example = "크로아상 외 1건") String orderName) {}
