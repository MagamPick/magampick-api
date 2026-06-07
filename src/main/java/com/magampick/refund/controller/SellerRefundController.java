package com.magampick.refund.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.refund.dto.RefundRejectRequest;
import com.magampick.refund.dto.RefundResponse;
import com.magampick.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 사장 환불 관리 API. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Refund (Seller)", description = "사장 환불 관리 API")
public class SellerRefundController {

  private final RefundService refundService;

  @GetMapping("/api/v1/seller/stores/{storeId}/refunds")
  @Operation(
      summary = "매장 환불 목록",
      description = "사장 본인 매장의 환불 요청 목록을 최신순으로 반환한다. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장")
  })
  public ResponseEntity<List<RefundResponse>> listStoreRefunds(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long storeId) {
    List<RefundResponse> result = refundService.listStoreRefunds(userDetails.getUserId(), storeId);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/api/v1/seller/refunds/{refundId}/approve")
  @Operation(
      summary = "환불 승인",
      description = "REQUESTED 상태의 환불 요청을 승인한다. 본인 매장 주문만 가능. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "승인 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장"),
    @ApiResponse(responseCode = "404", description = "환불 요청 없음"),
    @ApiResponse(responseCode = "409", description = "이미 처리된 환불 요청")
  })
  public ResponseEntity<RefundResponse> approveRefund(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long refundId) {
    RefundResponse result = refundService.approveRefund(userDetails.getUserId(), refundId);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/api/v1/seller/refunds/{refundId}/reject")
  @Operation(
      summary = "환불 거부",
      description = "REQUESTED 상태의 환불 요청을 거부한다. 거부 사유 필수. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "거부 성공"),
    @ApiResponse(responseCode = "400", description = "거부 사유 없음"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장"),
    @ApiResponse(responseCode = "404", description = "환불 요청 없음"),
    @ApiResponse(responseCode = "409", description = "이미 처리된 환불 요청")
  })
  public ResponseEntity<RefundResponse> rejectRefund(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long refundId,
      @Valid @RequestBody RefundRejectRequest request) {
    RefundResponse result = refundService.rejectRefund(userDetails.getUserId(), refundId, request);
    return ResponseEntity.ok(result);
  }
}
