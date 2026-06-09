package com.magampick.analytics.controller;

import com.magampick.analytics.domain.AnalyticsPeriod;
import com.magampick.analytics.dto.AnalyticsResponse;
import com.magampick.analytics.service.AnalyticsService;
import com.magampick.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사장 통계 대시보드 API. ROLE_SELLER 전용. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics (Seller)", description = "사장 통계 대시보드 API")
public class SellerAnalyticsController {

  private final AnalyticsService analyticsService;

  @GetMapping("/api/v1/seller/stores/{storeId}/analytics")
  @Operation(summary = "사장 통계 대시보드", description = "기간별 매출·주문·떨이·리뷰 집계. ROLE_SELLER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 기간 값 또는 파라미터 누락"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 또는 타인 매장"),
  })
  public AnalyticsResponse getAnalytics(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @Parameter(description = "집계 기간 (today|week|month|year)", required = true) @RequestParam
          AnalyticsPeriod period) {
    return analyticsService.getAnalytics(userDetails.getUserId(), storeId, period);
  }
}
