package com.magampick.store.controller;

import com.magampick.clearance.dto.StoreDealResponse;
import com.magampick.clearance.service.StoreDealQueryService;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.product.dto.StoreMenuItemResponse;
import com.magampick.store.dto.ConsumerStoreDetailResponse;
import com.magampick.store.dto.StoreListResponse;
import com.magampick.store.dto.StoreSort;
import com.magampick.store.service.StoreDetailQueryService;
import com.magampick.store.service.StoreMenuQueryService;
import com.magampick.store.service.StoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 매장 조회 API. 기존 사장 전용 {@link StoreController}({@code /api/v1/seller/stores}) 와 URL 충돌 없음. */
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Store (Consumer)", description = "소비자 매장 조회 API")
public class StoreQueryController {

  private final StoreQueryService storeQueryService;
  private final StoreDetailQueryService storeDetailQueryService;
  private final StoreDealQueryService storeDealQueryService;
  private final StoreMenuQueryService storeMenuQueryService;

  @GetMapping
  @Operation(
      summary = "전체 매장 조회",
      description =
          "소비자 기본 주소지 5km 이내 OPEN 매장 목록. 5종 정렬(recommended/distance/discount/closing/rating). ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)")
  })
  public ResponseEntity<StoreListResponse> list(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(defaultValue = "recommended") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    StoreSort storeSort = StoreSort.fromParam(sort);
    return ResponseEntity.ok(
        storeQueryService.getStores(userDetails.getUserId(), storeSort, page, size));
  }

  @GetMapping("/{storeId}")
  @Operation(
      summary = "매장 상세 조회",
      description = "매장 헤더/정보탭 데이터. 영업상태 무관 조회. 기본 주소지 기반 거리·isFavorite. ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)"),
    @ApiResponse(responseCode = "404", description = "매장 없음 (STORE_NOT_FOUND)")
  })
  public ResponseEntity<ConsumerStoreDetailResponse> detail(
      @PathVariable Long storeId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(storeDetailQueryService.getDetail(storeId, userDetails.getUserId()));
  }

  @GetMapping("/{storeId}/clearance-items")
  @Operation(summary = "마감할인 탭 조회", description = "매장의 활성(OPEN) 마감할인 목록. 인증 불요(public).")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  public ResponseEntity<List<StoreDealResponse>> deals(@PathVariable Long storeId) {
    return ResponseEntity.ok(storeDealQueryService.getActiveDeals(storeId));
  }

  @GetMapping("/{storeId}/menu")
  @Operation(summary = "메뉴 탭 조회", description = "매장의 ON_SALE 상품 목록 (flat). 인증 불요(public).")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  public ResponseEntity<List<StoreMenuItemResponse>> menu(@PathVariable Long storeId) {
    return ResponseEntity.ok(storeMenuQueryService.getMenu(storeId));
  }
}
