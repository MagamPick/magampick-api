package com.magampick.settlement.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 정산 요약 카드 응답. FE SettlementSummary 타입 대응. */
public record SettlementSummaryResponse(
    Long cycleId,
    /** "M월 N차 · M/D1~M/D2" 형식. 예: "6월 1차 · 6/1~6/15" */
    String periodLabel,
    BigDecimal netAmount,
    OffsetDateTime depositDate,
    String status) {}
