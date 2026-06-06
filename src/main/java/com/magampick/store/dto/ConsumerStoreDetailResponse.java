package com.magampick.store.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "소비자 매장 상세 응답")
public record ConsumerStoreDetailResponse(
    @Schema(description = "매장 ID", example = "1") Long id,
    @Schema(description = "매장 이름", example = "동네빵집") String name,
    @Schema(description = "대표 이미지 URL (없으면 null)") String imageUrl,
    @Schema(description = "현재 영업 상태 (OPEN/BREAK/CLOSED_TODAY)") OperationStatus businessStatus,
    @Schema(description = "오늘 영업 종료 시각 (HH:mm). 오늘 휴무이면 null", example = "21:00")
        String closingTime,
    @Schema(description = "평균 평점", example = "4.3") double rating,
    @Schema(description = "리뷰 수", example = "12") long reviewCount,
    @Schema(description = "기본 주소지에서의 거리 (km)", example = "1.2") double distanceKm,
    @Schema(description = "단골 여부") boolean isFavorite,
    @Schema(description = "도로명+상세 주소", example = "서울시 중구 세종대로 110") String address,
    @Schema(description = "전화번호", example = "02-1234-5678") String phone,
    @Schema(description = "사업자 번호", example = "1234567890") String businessNumber,
    @Schema(description = "영업시간 (월~일 7개)") List<OperatingHourResponse> operatingHours,
    @Schema(description = "매장 위도 (WGS84)", example = "37.5665") double lat,
    @Schema(description = "매장 경도 (WGS84)", example = "126.9780") double lng) {}
