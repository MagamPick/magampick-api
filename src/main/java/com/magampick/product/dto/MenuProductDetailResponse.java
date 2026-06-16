package com.magampick.product.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "소비자 일반 상품 상세 응답 (kind=menu)")
public record MenuProductDetailResponse(
    @Schema(description = "상품 종류 구분자", example = "menu") String kind,
    @Schema(description = "상품 ID") Long id,
    @Schema(description = "매장 ID") Long storeId,
    @Schema(description = "매장명") String storeName,
    @Schema(description = "기본 주소지에서의 거리 (km)", example = "0.8") double distanceKm,
    @Schema(description = "매장 영업 상태 (OPEN/BREAK/CLOSED_TODAY)") OperationStatus businessStatus,
    @Schema(description = "상품 이미지 URL (없으면 null)") String imageUrl,
    @Schema(description = "상품명") String name,
    @Schema(description = "상품 설명 (없으면 null)") String description,
    @Schema(description = "평균 평점 (일반 상품은 항상 0.0)", example = "0.0") double rating,
    @Schema(description = "리뷰 수 (일반 상품은 항상 0)", example = "0") long reviewCount,
    @Schema(description = "오늘 영업 종료 시각 (HH:mm). 오늘 휴무이면 null", example = "21:00")
        String closingTime,
    @Schema(description = "가격 (원)") BigDecimal price,
    @Schema(description = "판매 여부 (ON_SALE=true)") boolean isOnSale,
    @Schema(description = "활성 떨이 존재 여부 (true 면 장바구니 담기 불가)") boolean hasActiveDeal) {}
