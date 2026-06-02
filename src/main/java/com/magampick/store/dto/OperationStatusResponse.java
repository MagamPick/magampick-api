package com.magampick.store.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "매장 영업 상태 응답 (사장 화면용)")
public record OperationStatusResponse(
    @Schema(description = "매장 ID", example = "1") Long storeId,
    @Schema(description = "현재 영업 상태", example = "CLOSED_TODAY") OperationStatus operationStatus,
    @Schema(description = "오늘 요일이 영업 요일인지 — `OPEN` 전환 가능 조건", example = "false")
        boolean canOpenToday,
    @Schema(description = "오늘 요일의 마감 시각 (영업 요일일 때만, HH:mm)", example = "21:00")
        LocalTime todayCloseTime) {}
