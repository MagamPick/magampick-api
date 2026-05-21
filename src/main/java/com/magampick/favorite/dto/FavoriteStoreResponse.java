package com.magampick.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "즐겨찾기 목록 아이템")
public record FavoriteStoreResponse(
    @Schema(description = "매장 ID", example = "1") Long storeId,
    @Schema(description = "매장명", example = "동네빵집") String storeName,
    @Schema(description = "도로명 주소", example = "서울시 강남구 테헤란로 1") String roadAddress,
    @Schema(description = "매장 이미지 URL") String imageUrl,
    @Schema(description = "즐겨찾기 등록 시각") OffsetDateTime createdAt) {}
