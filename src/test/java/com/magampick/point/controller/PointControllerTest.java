package com.magampick.point.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.point.domain.PointReason;
import com.magampick.point.dto.PointHistoryFilter;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.dto.PointTransactionResponse;
import com.magampick.point.fixture.PointFixture;
import com.magampick.point.service.PointService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PointController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PointControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean PointService pointService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  // ── GET /api/v1/customers/me/points/summary ──────────────────────────────────

  @Test
  void 잔액_조회_성공() throws Exception {
    // given
    given(pointService.getSummary(eq(1L))).willReturn(new PointSummaryResponse(3000L, 500L));

    // when / then
    mockMvc
        .perform(get("/api/v1/customers/me/points/summary").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.balance").value(3000))
        .andExpect(jsonPath("$.data.pendingPoints").value(500));
  }

  @Test
  void 잔액_조회_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/points/summary"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 잔액_조회_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/points/summary").with(user(SELLER_USER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/customers/me/points/history ──────────────────────────────────

  @Test
  void 내역_조회_성공() throws Exception {
    // given
    List<PointTransactionResponse> responses =
        List.of(
            PointFixture.aTransactionResponse(10L, PointReason.EARN),
            PointFixture.aTransactionResponse(11L, PointReason.USE));
    given(pointService.getHistory(eq(1L), eq(PointHistoryFilter.ALL))).willReturn(responses);

    // when / then
    mockMvc
        .perform(get("/api/v1/customers/me/points/history").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].id").value(10))
        .andExpect(jsonPath("$.data[0].reason").value("EARN"))
        .andExpect(jsonPath("$.data[1].id").value(11))
        .andExpect(jsonPath("$.data[1].reason").value("USE"));
  }

  @Test
  void 내역_조회_필터_EARN() throws Exception {
    // given
    List<PointTransactionResponse> responses =
        List.of(PointFixture.aTransactionResponse(10L, PointReason.EARN));
    given(pointService.getHistory(eq(1L), eq(PointHistoryFilter.EARN))).willReturn(responses);

    // when / then
    mockMvc
        .perform(get("/api/v1/customers/me/points/history?filter=EARN").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].reason").value("EARN"));
  }

  @Test
  void 내역_조회_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/points/history"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 내역_조회_403_사장_접근_거부() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/points/history").with(user(SELLER_USER)))
        .andExpect(status().isForbidden());
  }
}
