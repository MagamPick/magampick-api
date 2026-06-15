package com.magampick.payment.service;

import java.math.BigDecimal;

/** 결제 승인 요청. paymentKey = 토스 SDK 발급 키 (stub 은 null), idempotencyKey = 토스 orderId ("order-{id}"). */
public record PaymentCommand(
    String paymentKey, String idempotencyKey, BigDecimal amount, String method) {}
