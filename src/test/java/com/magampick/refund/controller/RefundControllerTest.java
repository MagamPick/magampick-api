package com.magampick.refund.controller;

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
import com.magampick.order.exception.OrderErrorCode;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.exception.RefundErrorCode;
import com.magampick.refund.fixture.RefundFixture;
import com.magampick.refund.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RefundController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class RefundControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean RefundService refundService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final Long ORDER_ID = 42L;

  // ── 성공 ─────────────────────────────────────────────────────────────────

  @Test
  void 환불요청_200() throws Exception {
    // given
    given(refundService.requestRefund(eq(1L), eq(ORDER_ID), any()))
        .willReturn(OrderFixture.anOrderResponse(ORDER_ID));

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest()))
                .with(user(CUSTOMER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  // ── 입력 검증 ─────────────────────────────────────────────────────────────

  @Test
  void 환불요청_사유없음_400() throws Exception {
    // given — reason 빈 문자열 → @Valid 에서 거부
    RefundRequestRequest emptyReason = new RefundRequestRequest("");

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyReason))
                .with(user(CUSTOMER)))
        .andExpect(status().isBadRequest());
  }

  // ── 인증·인가 ─────────────────────────────────────────────────────────────

  @Test
  void 환불요청_미인증_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 환불요청_셀러인증_403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest()))
                .with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── 비즈니스 예외 ─────────────────────────────────────────────────────────

  @Test
  void 환불요청_주문없음_404() throws Exception {
    // given
    willThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND))
        .given(refundService)
        .requestRefund(eq(1L), eq(ORDER_ID), any());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest()))
                .with(user(CUSTOMER)))
        .andExpect(status().isNotFound());
  }

  @Test
  void 환불요청_미완료주문_409() throws Exception {
    // given
    willThrow(new BusinessException(RefundErrorCode.REFUND_NOT_COMPLETED_ORDER))
        .given(refundService)
        .requestRefund(eq(1L), eq(ORDER_ID), any());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest()))
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict());
  }

  @Test
  void 환불요청_기간초과_409() throws Exception {
    // given
    willThrow(new BusinessException(RefundErrorCode.REFUND_WINDOW_EXPIRED))
        .given(refundService)
        .requestRefund(eq(1L), eq(ORDER_ID), any());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest()))
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict());
  }

  @Test
  void 환불요청_중복_409() throws Exception {
    // given
    willThrow(new BusinessException(RefundErrorCode.REFUND_ALREADY_REQUESTED))
        .given(refundService)
        .requestRefund(eq(1L), eq(ORDER_ID), any());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/refund", ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRefundRequest()))
                .with(user(CUSTOMER)))
        .andExpect(status().isConflict());
  }
}
