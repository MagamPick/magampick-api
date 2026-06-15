package com.magampick.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 환불 목록에 포함되는 주문 항목 요약. */
@Schema(description = "환불 주문 항목")
public record RefundItemResponse(
    @Schema(description = "상품명", example = "크로아상") String name,
    @Schema(description = "수량", example = "2") int quantity,
    @Schema(description = "결제 단가", example = "3000") BigDecimal price) {}
