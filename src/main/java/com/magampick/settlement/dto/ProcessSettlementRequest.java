package com.magampick.settlement.dto;

import java.time.LocalDate;

/** 정산 배치 수동 트리거 요청. targetDate 미입력 시 오늘 기준. */
public record ProcessSettlementRequest(LocalDate targetDate) {}
