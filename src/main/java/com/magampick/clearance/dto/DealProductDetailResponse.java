package com.magampick.clearance.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "소비자 떨이 상품 상세 응답 (kind=deal)")
public record DealProductDetailResponse(
    @Schema(description = "상품 종류 구분자", example = "deal") String kind,
    @Schema(description = "떨이 상품 ID") Long id,
    @Schema(description = "매장 ID") Long storeId,
    @Schema(description = "매장명") String storeName,
    @Schema(description = "기본 주소지에서의 거리 (km)", example = "1.2") double distanceKm,
    @Schema(description = "매장 영업 상태 (OPEN/BREAK/CLOSED_TODAY)") OperationStatus businessStatus,
    @Schema(description = "상품 이미지 URL (없으면 null)") String imageUrl,
    @Schema(description = "상품명") String name,
    @Schema(description = "상품 설명 (없으면 null)") String description,
    @Schema(description = "평균 평점", example = "4.3") double rating,
    @Schema(description = "리뷰 수", example = "12") long reviewCount,
    @Schema(description = "오늘 영업 종료 시각 (HH:mm). 오늘 휴무이면 null", example = "21:00")
        String closingTime,
    @Schema(description = "정상가 (원)") BigDecimal originalPrice,
    @Schema(description = "판매가 (원)") BigDecimal salePrice,
    @Schema(description = "할인율 (%)", example = "33") int discountRate,
    @Schema(description = "픽업 마감 일시 (ISO-8601)") LocalDateTime pickupDeadline,
    @Schema(description = "잔여 수량") int stockLeft,
    @Schema(description = "떨이 상태 (ACTIVE/SOLD_OUT/EXPIRED)") String dealStatus) {}
