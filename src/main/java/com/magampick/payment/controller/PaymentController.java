package com.magampick.payment.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.order.dto.OrderResponse;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.payment.service.TossConfirmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결제 API. 현재: 토스 샌드박스 결제 확인. */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 API")
public class PaymentController {

  private final TossConfirmService tossConfirmService;

  @PostMapping("/toss/confirm")
  @Operation(
      summary = "토스 결제 확인",
      description =
          "토스 SDK 결제 후 paymentKey·orderId·amount 를 서버로 전달해 최종 승인한다. 주문이 PENDING 으로 활성화된다."
              + " ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "결제 확인 성공, 주문 PENDING 활성화"),
    @ApiResponse(responseCode = "400", description = "금액 불일치"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 주문"),
    @ApiResponse(responseCode = "404", description = "주문 없음"),
    @ApiResponse(responseCode = "409", description = "결제 대기 상태가 아님"),
    @ApiResponse(responseCode = "502", description = "토스 API 오류")
  })
  public ResponseEntity<OrderResponse> confirmTossPayment(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody TossConfirmRequest request) {
    OrderResponse response = tossConfirmService.confirmPayment(userDetails.getUserId(), request);
    return ResponseEntity.ok(response);
  }
}
