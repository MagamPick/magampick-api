package com.magampick.payment.service;

import com.magampick.payment.domain.PaymentStatus;
import java.time.LocalDateTime;

/** 결제 취소 결과. paymentKey = PG 발급 키, status = CANCELED, cancelledAt = 취소 시각. */
public record PaymentCancellation(
    String paymentKey, PaymentStatus status, LocalDateTime cancelledAt) {}
