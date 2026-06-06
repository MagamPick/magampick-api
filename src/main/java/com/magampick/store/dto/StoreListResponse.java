package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 소비자 매장 목록 응답. 표준 {@code SliceResponse} 에 {@code total}/{@code dealStoreCount} 집계 필드를 추가한 전용 응답.
 *
 * <p>FE 계약: nextCursor = hasNext ? page + 1 : null.
 */
@Schema(description = "소비자 매장 목록 응답")
public record StoreListResponse(
    @Schema(description = "매장 목록") List<StoreListItemResponse> items,
    @Schema(description = "현재 페이지 (0-based)", example = "0") int page,
    @Schema(description = "페이지당 아이템 수", example = "20") int size,
    @Schema(description = "다음 페이지 존재 여부") boolean hasNext,
    @Schema(description = "전체 후보 매장 수 (5km 이내 OPEN 오늘영업)", example = "12") long total,
    @Schema(description = "활성 떨이가 있는 매장 수", example = "5") long dealStoreCount) {}
