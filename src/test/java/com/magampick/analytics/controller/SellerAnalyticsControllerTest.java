package com.magampick.analytics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.analytics.domain.AnalyticsPeriod;
import com.magampick.analytics.fixture.AnalyticsFixture;
import com.magampick.analytics.service.AnalyticsService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.exception.StoreErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerAnalyticsController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerAnalyticsControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean AnalyticsService analyticsService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── GET /api/v1/seller/stores/{storeId}/analytics?period=today ───────────────

  @Test
  void 통계_조회_성공_200_envelope() throws Exception {
    given(analyticsService.getAnalytics(eq(2L), eq(10L), eq(AnalyticsPeriod.TODAY)))
        .willReturn(AnalyticsFixture.aResponse());

    mockMvc
        .perform(
            get("/api/v1/seller/stores/10/analytics").param("period", "today").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.sales.totalSales").value(50000))
        .andExpect(jsonPath("$.data.orders.total").value(5))
        .andExpect(jsonPath("$.data.clearance.soldQty").value(10))
        .andExpect(jsonPath("$.data.review.newCount").value(3));
  }

  @Test
  void period_소문자_바인딩_today() throws Exception {
    given(analyticsService.getAnalytics(any(), any(), eq(AnalyticsPeriod.TODAY)))
        .willReturn(AnalyticsFixture.aResponse());

    // 소문자 "today" → AnalyticsPeriod.TODAY 로 변환돼야 함
    mockMvc
        .perform(
            get("/api/v1/seller/stores/10/analytics").param("period", "today").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void 잘못된_period_400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/seller/stores/10/analytics").param("period", "invalid").with(user(SELLER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_PERIOD"));
  }

  @Test
  void period_누락_400() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/10/analytics").with(user(SELLER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void SELLER_아니면_403() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/seller/stores/10/analytics").param("period", "today").with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 타인매장_403() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED))
        .given(analyticsService)
        .getAnalytics(any(), any(), any());

    mockMvc
        .perform(
            get("/api/v1/seller/stores/10/analytics").param("period", "today").with(user(SELLER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }
}
