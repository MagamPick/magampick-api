package com.magampick.store.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.store.dto.StoreListResponse;
import com.magampick.store.dto.StoreSort;
import com.magampick.store.service.StoreQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}
