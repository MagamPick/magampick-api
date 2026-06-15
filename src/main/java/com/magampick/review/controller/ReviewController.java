package com.magampick.review.controller;

import com.magampick.global.response.SliceResponse;
import com.magampick.review.dto.ReviewSummaryResponse;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.service.ReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "매장 리뷰 조회 API (public). write 는 Phase 7 구현 예정.")
public class ReviewController {

  private final ReviewQueryService reviewQueryService;

  @GetMapping
  @Operation(summary = "매장 리뷰 목록 조회", description = "최신순 SliceResponse. 삭제된 리뷰 제외.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
  public SliceResponse<StoreReviewResponse> getReviews(
      @Parameter(description = "매장 ID") @PathVariable Long storeId,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return reviewQueryService.getStoreReviews(storeId, pageable);
  }

  @GetMapping("/summary")
  @Operation(summary = "매장 리뷰 요약 조회", description = "평균 별점 + 1~5점 분포 (5점 → 1점 순, 없는 별점은 count=0).")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
  public ReviewSummaryResponse getReviewSummary(
      @Parameter(description = "매장 ID") @PathVariable Long storeId) {
    return reviewQueryService.getReviewSummary(storeId);
  }
}
