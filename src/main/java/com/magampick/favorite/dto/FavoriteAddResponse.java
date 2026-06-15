package com.magampick.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "즐겨찾기 등록 응답")
public record FavoriteAddResponse(
    @Schema(description = "즐겨찾기한 매장 ID", example = "1") Long storeId,
    @Schema(description = "즐겨찾기 등록 시각") OffsetDateTime createdAt) {}
