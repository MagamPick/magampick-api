package com.magampick.refund.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.order.dto.OrderResponse;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 환불 요청 API. */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Refund (Consumer)", description = "소비자 환불 요청 API")
public class RefundController {

  private final RefundService refundService;

  @PostMapping("/{orderId}/refund")
  @Operation(
      summary = "환불 요청",
      description =
          "수령완료 주문에 대해 환불을 요청한다. COMPLETED 주문 / completedAt 후 3일 이내 / 1주문 1요청. ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "환불 요청 성공"),
    @ApiResponse(responseCode = "400", description = "사유 없음"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 주문"),
    @ApiResponse(responseCode = "404", description = "주문 없음"),
    @ApiResponse(responseCode = "409", description = "미완료 주문 / 기간 초과 / 중복 요청")
  })
  public ResponseEntity<OrderResponse> requestRefund(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long orderId,
      @Valid @RequestBody RefundRequestRequest request) {
    OrderResponse response = refundService.requestRefund(userDetails.getUserId(), orderId, request);
    return ResponseEntity.ok(response);
  }
}
