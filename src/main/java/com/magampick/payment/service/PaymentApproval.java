package com.magampick.payment.service;

import com.magampick.payment.domain.PaymentStatus;
import java.time.LocalDateTime;

/** 결제 승인 결과. paymentKey = PG 발급 키, status = APPROVED/FAILED, approvedAt = 승인 시각. */
public record PaymentApproval(String paymentKey, PaymentStatus status, LocalDateTime approvedAt) {}
