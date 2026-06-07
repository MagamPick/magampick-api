package com.magampick.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.settlement.dto.ProcessSettlementRequest;
import com.magampick.settlement.service.SettlementService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminSettlementController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AdminSettlementControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean SettlementService settlementService;
  @MockitoBean JwtProvider jwtProvider;
  @MockitoBean Clock clock;

  private static final CustomUserDetails ADMIN = new CustomUserDetails(99L, Role.ADMIN);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  // ── POST /api/v1/admin/settlements/process ───────────────────────────────────

  @Test
  void 정산_배치_트리거_200_targetDate_지정() throws Exception {
    given(settlementService.processBatch(LocalDate.of(2026, 6, 15))).willReturn(5);

    String body =
        objectMapper.writeValueAsString(new ProcessSettlementRequest(LocalDate.of(2026, 6, 15)));

    mockMvc
        .perform(
            post("/api/v1/admin/settlements/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.processedCount").value(5));
  }

  @Test
  void 정산_배치_트리거_200_targetDate_미입력_오늘기준() throws Exception {
    // Clock 고정: 2026-06-20
    given(clock.instant()).willReturn(Instant.parse("2026-06-20T00:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
    given(settlementService.processBatch(any())).willReturn(3);

    mockMvc
        .perform(post("/api/v1/admin/settlements/process").with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.processedCount").value(3));
  }

  @Test
  void 정산_배치_트리거_401_미인증() throws Exception {
    mockMvc.perform(post("/api/v1/admin/settlements/process")).andExpect(status().isUnauthorized());
  }

  @Test
  void 정산_배치_트리거_403_사장() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/settlements/process").with(user(SELLER)))
        .andExpect(status().isForbidden());
  }
}
