package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** 매장 영업시간 1행 (영업 요일만 — 휴무 요일은 클라이언트가 list 에서 제외해서 전송). 노션 "영업시간 설정". */
@Schema(description = "영업시간 1행 (영업 요일만)")
public record BusinessHourPayload(
    @Schema(description = "요일", example = "MONDAY") @NotNull DayOfWeek day,
    @Schema(description = "시작 시각 (24시간, 분 단위)", example = "09:00") @NotNull LocalTime openTime,
    @Schema(description = "종료 시각 (24시간, 분 단위, 시작 시각 이후)", example = "21:00") @NotNull
        LocalTime closeTime) {}
