package com.magampick.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.payment.exception.PaymentErrorCode;
import com.magampick.payment.service.TossConfirmService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PaymentControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean TossConfirmService tossConfirmService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);

  private String validConfirmJson() throws Exception {
    return objectMapper.writeValueAsString(
        new TossConfirmRequest("toss_paykey", 42L, new BigDecimal("6000")));
  }

  // ── 성공 ─────────────────────────────────────────────────────────────────────

  @Test
  void 결제_확인_200() throws Exception {
    given(tossConfirmService.confirmPayment(eq(1L), any()))
        .willReturn(OrderFixture.anOrderResponse(42L));

    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(42));
  }

  // ── 인증/인가 ─────────────────────────────────────────────────────────────────

  @Test
  void 미인증_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 판매자_접근_403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmJson())
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── 실패 ─────────────────────────────────────────────────────────────────────

  @Test
  void 금액_불일치_400() throws Exception {
    willThrow(new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH))
        .given(tossConfirmService)
        .confirmPayment(eq(1L), any());

    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_AMOUNT_MISMATCH"));
  }

  @Test
  void 상태_불일치_409() throws Exception {
    willThrow(new BusinessException(PaymentErrorCode.PAYMENT_STATUS_MISMATCH))
        .given(tossConfirmService)
        .confirmPayment(eq(1L), any());

    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validConfirmJson())
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_STATUS_MISMATCH"));
  }

  @Test
  void 입력검증_실패_400() throws Exception {
    String invalidJson =
        objectMapper.writeValueAsString(new TossConfirmRequest("", 42L, new BigDecimal("6000")));

    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest());
  }
}
