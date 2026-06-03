package com.magampick.store.dto;

import com.magampick.store.domain.OperationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사장 보유 매장 목록 응답 — 매장 전환 모달용 최소 필드 (매장명 + 영업 상태). 상세 정보는 {@link StoreDetailResponse} 사용. 노션 "보유 매장
 * 목록 조회".
 */
@Schema(description = "사장 보유 매장 목록 응답 (매장 전환 모달용)")
public record StoreResponse(
    @Schema(description = "매장 ID", example = "1") Long id,
    @Schema(description = "매장명", example = "동네빵집") String name,
    @Schema(description = "영업 상태", example = "OPEN") OperationStatus operationStatus) {}
