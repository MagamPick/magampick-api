package com.magampick.search.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.search.dto.SearchResultResponse;
import com.magampick.search.dto.SearchSuggestionResponse;
import com.magampick.search.service.SearchQueryService;
import com.magampick.store.dto.StoreSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 9 검색 API. ROLE_CUSTOMER 전용. */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search (Consumer)", description = "소비자 검색 API")
public class SearchController {

  private final SearchQueryService searchQueryService;

  @GetMapping
  @Operation(
      summary = "키워드 검색",
      description =
          "기본 주소지 5km 이내 OPEN 매장 세트에서 매장명·떨이명·상품명 부분 일치 검색. 빈 q → 빈 결과. 5종 정렬 지원. ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "검색 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)")
  })
  public ResponseEntity<SearchResultResponse> search(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "recommended") String sort) {
    StoreSort storeSort = StoreSort.fromParam(sort);
    return ResponseEntity.ok(searchQueryService.search(userDetails.getUserId(), q, storeSort));
  }

  @GetMapping("/autocomplete")
  @Operation(
      summary = "검색어 자동완성",
      description =
          "기본 주소지 5km 이내 매장 세트에서 word_similarity 기반 이름 제안. 1자 미만 → 빈 결과. 최대 10개, 유사도 내림차순. ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "자동완성 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)")
  })
  public ResponseEntity<List<SearchSuggestionResponse>> autocomplete(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(defaultValue = "") String q) {
    return ResponseEntity.ok(searchQueryService.autocomplete(userDetails.getUserId(), q));
  }
}
