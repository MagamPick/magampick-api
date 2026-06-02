package com.magampick.store.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "매장 영업 상태 전환 요청")
public record OperationStatusTransitionRequest(
    @Schema(description = "전환할 목표 상태", example = "OPEN") @NotNull OperationStatus to) {}
