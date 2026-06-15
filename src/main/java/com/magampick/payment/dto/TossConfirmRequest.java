package com.magampick.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 토스 결제 확인 요청. 토스 SDK 콜백에서 받은 paymentKey·orderId·amount 를 서버로 전달. */
@Schema(description = "토스 결제 확인 요청")
public record TossConfirmRequest(
    @Schema(description = "토스 발급 결제 키", example = "tgen_20240101abc") @NotBlank String paymentKey,
    @Schema(description = "주문 DB ID (PrepareOrderResponse.orderId)", example = "42") @NotNull
        Long orderId,
    @Schema(description = "결제 금액 (PrepareOrderResponse.amount 와 일치)", example = "6000")
        @NotNull
        @Positive
        BigDecimal amount) {}
