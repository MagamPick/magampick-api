package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 매장 영업시간 저장 요청 (전체 교체). 영업 요일만 list 에 담아 전송 — 휴무 요일은 list 에서 제외. 빈 list 도 허용 (모든 요일 휴무). */
@Schema(description = "매장 영업시간 저장 요청 (전체 교체, 영업 요일만)")
public record BusinessHoursSaveRequest(
    @Schema(description = "영업시간 목록 (영업 요일만, 빈 list 허용)") @NotNull @Size(max = 7) @Valid
        List<BusinessHourPayload> hours) {}
