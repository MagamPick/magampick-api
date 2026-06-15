package com.magampick.payment.service;

import java.math.BigDecimal;

/** 결제 취소 요청. paymentKey = PG 발급 키, cancelReason = 취소 사유, cancelAmount = 취소 금액. */
public record PaymentCancellationCommand(
    String paymentKey, String cancelReason, BigDecimal cancelAmount) {}
