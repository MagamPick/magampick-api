package com.magampick.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 자동완성 제안 아이템. kind = "store" | "product", text = 제안 이름. */
@Schema(description = "자동완성 제안 아이템")
public record SearchSuggestionResponse(
    @Schema(description = "제안 종류 (store | product)", example = "store") SuggestionKind kind,
    @Schema(description = "제안 텍스트") String text) {}
