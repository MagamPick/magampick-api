package com.magampick.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "즐겨찾기 등록 요청")
public record FavoriteAddRequest(
    @Schema(description = "매장 ID", example = "1") @NotNull Long storeId) {}
