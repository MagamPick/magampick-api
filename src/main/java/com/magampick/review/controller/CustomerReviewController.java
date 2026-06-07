package com.magampick.review.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.review.dto.CreateReviewRequest;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.ReviewableOrderResponse;
import com.magampick.review.dto.UpdateReviewRequest;
import com.magampick.review.service.ReviewCommandService;
import com.magampick.review.service.ReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 리뷰 작성·수정·삭제·조회 API. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Review (Customer)", description = "소비자 리뷰 작성·수정·삭제·조회 API")
public class CustomerReviewController {

  private final ReviewCommandService reviewCommandService;
  private final ReviewQueryService reviewQueryService;

  @GetMapping("/api/v1/orders/reviewable")
  @Operation(summary = "리뷰 작성 가능한 완료 주문 목록", description = "COMPLETED 주문 목록. reviewed/reviewId 포함.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
  public ResponseEntity<List<ReviewableOrderResponse>> getReviewableOrders(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(reviewQueryService.getReviewableOrders(userDetails.getUserId()));
  }

  @GetMapping("/api/v1/orders/{orderId}/review")
  @Operation(summary = "주문별 리뷰 조회", description = "리뷰 없으면 204 No Content.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "204", description = "리뷰 없음")
  })
  public ResponseEntity<MyReviewResponse> getOrderReview(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "주문 ID") @PathVariable Long orderId) {
    return reviewQueryService
        .getOrderReview(userDetails.getUserId(), orderId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build());
  }

  @GetMapping("/api/v1/customers/me/reviews")
  @Operation(summary = "소비자 본인 리뷰 목록", description = "최신순, 삭제 제외.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
  public ResponseEntity<List<MyReviewResponse>> getMyReviews(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return ResponseEntity.ok(reviewQueryService.getMyReviews(userDetails.getUserId()));
  }

  @PostMapping("/api/v1/orders/{orderId}/reviews")
  @Operation(summary = "리뷰 작성", description = "픽업 완료(COMPLETED) 주문에만 작성 가능. 201 + 생성된 리뷰 반환.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "리뷰 작성 성공"),
    @ApiResponse(responseCode = "409", description = "이미 리뷰 존재 / COMPLETED 아님")
  })
  public ResponseEntity<MyReviewResponse> createReview(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "주문 ID") @PathVariable Long orderId,
      @Valid @RequestBody CreateReviewRequest request) {
    MyReviewResponse result =
        reviewCommandService.createReview(userDetails.getUserId(), orderId, request);
    return ResponseEntity.created(URI.create("/api/v1/reviews/" + result.id())).body(result);
  }

  @PutMapping("/api/v1/reviews/{reviewId}")
  @Operation(summary = "리뷰 수정", description = "본인 리뷰, 사장 답글 없는 경우만 수정 가능. 200 + 수정된 리뷰 반환.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "409", description = "답글 잠금")
  })
  public ResponseEntity<MyReviewResponse> updateReview(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "리뷰 ID") @PathVariable Long reviewId,
      @Valid @RequestBody UpdateReviewRequest request) {
    return ResponseEntity.ok(
        reviewCommandService.updateReview(userDetails.getUserId(), reviewId, request));
  }

  @DeleteMapping("/api/v1/reviews/{reviewId}")
  @Operation(summary = "리뷰 삭제 (soft)", description = "본인 리뷰, 사장 답글 없는 경우만 삭제 가능. 204.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(responseCode = "409", description = "답글 잠금")
  })
  public ResponseEntity<Void> deleteReview(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Parameter(description = "리뷰 ID") @PathVariable Long reviewId) {
    reviewCommandService.deleteReview(userDetails.getUserId(), reviewId);
    return ResponseEntity.noContent().build();
  }
}
