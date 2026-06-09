package com.magampick.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** FAQ 응답 DTO. */
@Schema(description = "FAQ 응답")
public record FaqResponse(
    @Schema(description = "FAQ ID") Long id,
    @Schema(description = "질문") String question,
    @Schema(description = "답변") String answer) {}
