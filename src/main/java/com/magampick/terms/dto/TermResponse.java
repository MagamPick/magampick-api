package com.magampick.terms.dto;

import com.magampick.terms.domain.TermType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관 응답")
public record TermResponse(
    @Schema(description = "약관 ID", example = "1") Long id,
    @Schema(description = "약관 타입", example = "TERMS_OF_SERVICE") TermType type,
    @Schema(description = "약관 버전", example = "1") int version,
    @Schema(description = "약관 제목", example = "서비스 이용약관") String title,
    @Schema(description = "약관 본문") String body,
    @Schema(description = "필수 동의 여부", example = "true") boolean required) {}
