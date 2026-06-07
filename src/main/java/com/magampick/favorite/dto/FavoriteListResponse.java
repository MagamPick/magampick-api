package com.magampick.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "단골 매장 목록 응답")
public record FavoriteListResponse(
    @Schema(description = "단골 매장 목록") List<FavoriteStoreResponse> stores,
    @Schema(description = "총 단골 수", example = "5") long totalCount,
    @Schema(description = "전체 단골의 활성 마감할인 합계", example = "8") long totalActiveDealCount) {}
