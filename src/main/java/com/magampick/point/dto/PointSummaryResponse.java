package com.magampick.point.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 소비자 포인트 잔액 요약 응답. */
public record PointSummaryResponse(
    @Schema(description = "사용 가능 포인트 잔액") long balance,
    @Schema(description = "적립 예정 포인트 — 환불 윈도우(3일) 종료 후 사용 가능") long pendingPoints) {}
