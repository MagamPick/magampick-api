package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이메일 사용 가능 여부 응답")
public record EmailAvailabilityResponse(
    @Schema(description = "사용 가능 여부", example = "true") boolean available) {}
