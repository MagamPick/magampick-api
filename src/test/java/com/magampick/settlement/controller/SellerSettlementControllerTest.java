package com.magampick.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.settlement.exception.SettlementErrorCode;
import com.magampick.settlement.fixture.SettlementFixture;
import com.magampick.settlement.service.SettlementService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerSettlementController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerSettlementControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean SettlementService settlementService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── GET /api/v1/seller/stores/{storeId}/settlements ──────────────────────────

  @Test
  void 정산_목록_조회_200() throws Exception {
    given(settlementService.listSettlements(any(), any()))
        .willReturn(List.of(SettlementFixture.aCycleResponse(1L)));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].storeId").value(10))
        .andExpect(jsonPath("$.data[0].year").value(2026))
        .andExpect(jsonPath("$.data[0].half").value(1));
  }

  @Test
  void 정산_목록_조회_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 정산_목록_조회_403_소비자() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements").with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 정산_목록_조회_403_타인매장() throws Exception {
    willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_STORE_FORBIDDEN))
        .given(settlementService)
        .listSettlements(any(), any());

    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements").with(user(SELLER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("SETTLEMENT_STORE_FORBIDDEN"));
  }

  // ── GET /api/v1/seller/stores/{storeId}/settlements/summary ─────────────────

  @Test
  void 정산_요약_조회_200() throws Exception {
    given(settlementService.getSettlementSummary(any(), any()))
        .willReturn(Optional.of(SettlementFixture.aSummaryResponse(1L)));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements/summary").with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cycleId").value(1))
        .andExpect(jsonPath("$.data.periodLabel").value("6월 1차 · 6/1~6/15"))
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
  }

  @Test
  void 정산_요약_조회_204_회차없음() throws Exception {
    given(settlementService.getSettlementSummary(any(), any())).willReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements/summary").with(user(SELLER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 정산_요약_조회_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements/summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 정산_요약_조회_403_타인매장() throws Exception {
    willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_STORE_FORBIDDEN))
        .given(settlementService)
        .getSettlementSummary(any(), any());

    mockMvc
        .perform(get("/api/v1/seller/stores/10/settlements/summary").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }
}
