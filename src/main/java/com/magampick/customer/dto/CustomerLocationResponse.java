package com.magampick.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "소비자 현재 위치 갱신 응답")
public record CustomerLocationResponse(
    @Schema(description = "위도", example = "37.5665") double latitude,
    @Schema(description = "경도", example = "126.9780") double longitude,
    @Schema(description = "위치 갱신 시각 (KST)") LocalDateTime locationUpdatedAt) {}
