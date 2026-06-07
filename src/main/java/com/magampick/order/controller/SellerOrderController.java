package com.magampick.order.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.order.dto.SellerOrderResponse;
import com.magampick.order.service.OrderService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사장 주문 조회 API. Phase 5B step1: 매장별 주문 목록 + 단건 상세. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Order (Seller)", description = "사장 주문 조회 API")
public class SellerOrderController {

  private final OrderService orderService;

  @GetMapping("/api/v1/seller/stores/{storeId}/orders")
  @Operation(
      summary = "매장 주문 목록",
      description =
          "사장 본인 매장의 주문 목록. segment=ALL(기본)/PENDING/PREPARING/READY/COMPLETED/CANCELLED. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장"),
    @ApiResponse(responseCode = "404", description = "매장 없음")
  })
  public ResponseEntity<List<SellerOrderResponse>> listStoreOrders(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @RequestParam(defaultValue = "ALL") String segment) {
    List<SellerOrderResponse> result =
        orderService.listStoreOrders(userDetails.getUserId(), storeId, segment);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/api/v1/seller/orders/{id}")
  @Operation(
      summary = "사장 주문 상세",
      description = "사장 본인 매장 주문 단건 조회. 타인 매장 주문 접근 시 403. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장 주문"),
    @ApiResponse(responseCode = "404", description = "주문 없음")
  })
  public ResponseEntity<SellerOrderResponse> getStoreOrder(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    SellerOrderResponse result = orderService.getStoreOrder(userDetails.getUserId(), id);
    return ResponseEntity.ok(result);
  }
}
