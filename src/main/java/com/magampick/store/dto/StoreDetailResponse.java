package com.magampick.store.dto;

import com.magampick.store.domain.StoreStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "사장 매장 상세 응답")
public record StoreDetailResponse(
    @Schema(description = "매장 ID", example = "1") Long id,
    @Schema(description = "매장명", example = "동네빵집") String name,
    @Schema(description = "도로명 주소") String roadAddress,
    @Schema(description = "지번 주소") String jibunAddress,
    @Schema(description = "상세 주소") String detailAddress,
    @Schema(description = "우편번호") String zonecode,
    @Schema(description = "위도") Double latitude,
    @Schema(description = "경도") Double longitude,
    @Schema(description = "매장 전화번호") String phone,
    @Schema(description = "매장 소개") String description,
    @Schema(description = "대표 사진 URL") String imageUrl,
    @Schema(description = "매장 상태") StoreStatus status,
    @Schema(description = "반려 사유 (REJECTED 상태일 때만 노출)") String rejectionReason,
    @Schema(description = "카테고리 목록") List<String> categories,
    @Schema(description = "등록 신청 시각") OffsetDateTime createdAt) {}
