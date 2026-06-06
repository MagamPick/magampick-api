package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "영업시간 (요일 1건)")
public record OperatingHourResponse(
    @Schema(description = "요일 (월/화/수/목/금/토/일)", example = "월") String day,
    @Schema(description = "오픈 시각 (HH:mm). 휴무이면 null", example = "09:00") String open,
    @Schema(description = "마감 시각 (HH:mm). 휴무이면 null", example = "21:00") String close,
    @Schema(description = "휴무 여부. true = 해당 요일 영업 없음", example = "false") boolean closed) {}
