package com.magampick.settlement.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 정산 회차 상세 응답. FE SettlementCycle 타입 대응. */
public record SettlementCycleResponse(
    Long id,
    Long storeId,
    int year,
    int month,
    int half,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    OffsetDateTime depositDate,
    BigDecimal grossAmount,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    String status) {}
