package com.magampick.review.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.review.dto.ReviewReplyRequest;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.service.ReviewCommandService;
import com.magampick.review.service.ReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 사장 리뷰 조회·답글 API. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Review (Seller)", description = "사장 리뷰 조회·답글 API")
public class SellerReviewController {

  private final ReviewCommandService reviewCommandService;
  private final ReviewQueryService reviewQueryService;

  @GetMapping("/api/v1/seller/stores/{storeId}/reviews")
  @Operation(summary = "사장 본인 매장 리뷰 목록", description = "본인 매장에 달린 리뷰 목록 (최신순, 삭제 제외). 사장 답글 포함.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "403", description = "본인 매장 아님")
  })
  public List<StoreReviewResponse> getStoreReviews(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "매장 ID") @PathVariable Long storeId) {
    return reviewQueryService.getSellerStoreReviews(userDetails.getUserId(), storeId);
  }

  @PostMapping("/api/v1/seller/reviews/{reviewId}/reply")
  @Operation(
      summary = "사장 답글 작성",
      description = "본인 매장의 리뷰에만 답글 작성 가능. 리뷰당 1개. 201 + 답글 포함된 리뷰 반환.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "답글 작성 성공"),
    @ApiResponse(responseCode = "403", description = "본인 매장 리뷰 아님"),
    @ApiResponse(responseCode = "409", description = "이미 답글 존재")
  })
  public ResponseEntity<StoreReviewResponse> replyToReview(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "리뷰 ID") @PathVariable Long reviewId,
      @Valid @RequestBody ReviewReplyRequest request) {
    StoreReviewResponse result =
        reviewCommandService.replyToReview(userDetails.getUserId(), reviewId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }
}
