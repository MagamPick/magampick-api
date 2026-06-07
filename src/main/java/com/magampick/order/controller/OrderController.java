package com.magampick.order.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 주문 API. Phase 5A: 주문 생성 + stub 결제. */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order (Consumer)", description = "소비자 주문 API")
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  @Operation(
      summary = "주문 생성",
      description = "장바구니 내용으로 주문을 생성한다. 검증 → 재고차감 → stub 결제 자동승인 → 픽업코드 발급. ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 / 결제 미동의 / 금액 불일치 / 픽업 시간 오류"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)"),
    @ApiResponse(responseCode = "404", description = "매장/상품 없음"),
    @ApiResponse(responseCode = "409", description = "매장 영업 중지 / 재고 부족 / 떨이 마감 / 결제 실패")
  })
  public ResponseEntity<OrderResponse> createOrder(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody CreateOrderRequest request) {
    OrderResponse response = orderService.createOrder(userDetails.getUserId(), request);
    return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
  }
}
