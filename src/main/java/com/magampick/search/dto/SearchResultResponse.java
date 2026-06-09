package com.magampick.search.dto;

import com.magampick.store.dto.StoreListItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Phase 9 검색 결과 응답. stores(매장 카드) + products(떨이·메뉴 아이템 discriminated union). */
@Schema(description = "검색 결과")
public record SearchResultResponse(
    @Schema(description = "매장 목록") List<StoreListItemResponse> stores,
    @Schema(description = "상품 목록 (kind: deal | menu)") List<SearchProductItemResponse> products) {}
