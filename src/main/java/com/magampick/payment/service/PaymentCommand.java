package com.magampick.payment.service;

import java.math.BigDecimal;

/**
 * 결제 승인 요청. idempotencyKey = 주문 단위 멱등키 (중복 방지). Phase 5A stub 은 자동 승인 — 실 토스 연동 시 이 커맨드로
 * TossPaymentClient 를 호출한다.
 */
public record PaymentCommand(String idempotencyKey, BigDecimal amount, String method) {}
