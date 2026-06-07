package com.magampick.refund.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.magampick.refund.dto.RefundRejectRequest;
import com.magampick.refund.exception.RefundErrorCode;
import com.magampick.refund.fixture.RefundFixture;
import com.magampick.refund.service.RefundService;
import com.magampick.store.exception.StoreErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerRefundController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerRefundControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean RefundService refundService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final Long STORE_ID = 10L;
  private static final Long REFUND_ID = 1L;

  // ── 목록 조회 ─────────────────────────────────────────────────────────────

  @Test
  void 환불목록_200() throws Exception {
    // given
    given(refundService.listStoreRefunds(eq(2L), eq(STORE_ID)))
        .willReturn(List.of(RefundFixture.aRefundResponse(REFUND_ID)));

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/stores/{storeId}/refunds", STORE_ID).with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(REFUND_ID));
  }

  @Test
  void 환불목록_미인증_401() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/{storeId}/refunds", STORE_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 환불목록_소비자인증_403() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/stores/{storeId}/refunds", STORE_ID).with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 환불목록_타인매장_403() throws Exception {
    // given
    willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED))
        .given(refundService)
        .listStoreRefunds(eq(2L), eq(STORE_ID));

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/stores/{storeId}/refunds", STORE_ID).with(user(SELLER)))
        .andExpect(status().isForbidden());
  }

  // ── 환불 승인 ─────────────────────────────────────────────────────────────

  @Test
  void 환불승인_200() throws Exception {
    // given
    given(refundService.approveRefund(eq(2L), eq(REFUND_ID)))
        .willReturn(RefundFixture.aRefundResponse(REFUND_ID));

    // when / then
    mockMvc
        .perform(post("/api/v1/seller/refunds/{refundId}/approve", REFUND_ID).with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(REFUND_ID));
  }

  @Test
  void 환불승인_미인증_401() throws Exception {
    mockMvc
        .perform(post("/api/v1/seller/refunds/{refundId}/approve", REFUND_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 환불승인_소비자인증_403() throws Exception {
    mockMvc
        .perform(post("/api/v1/seller/refunds/{refundId}/approve", REFUND_ID).with(user(CUSTOMER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 환불승인_이미처리됨_409() throws Exception {
    // given
    willThrow(new BusinessException(RefundErrorCode.REFUND_ALREADY_PROCESSED))
        .given(refundService)
        .approveRefund(eq(2L), eq(REFUND_ID));

    // when / then
    mockMvc
        .perform(post("/api/v1/seller/refunds/{refundId}/approve", REFUND_ID).with(user(SELLER)))
        .andExpect(status().isConflict());
  }

  @Test
  void 환불승인_없는환불_404() throws Exception {
    // given
    willThrow(new BusinessException(RefundErrorCode.REFUND_NOT_FOUND))
        .given(refundService)
        .approveRefund(eq(2L), eq(REFUND_ID));

    // when / then
    mockMvc
        .perform(post("/api/v1/seller/refunds/{refundId}/approve", REFUND_ID).with(user(SELLER)))
        .andExpect(status().isNotFound());
  }

  // ── 환불 거부 ─────────────────────────────────────────────────────────────

  @Test
  void 환불거부_200() throws Exception {
    // given
    given(refundService.rejectRefund(eq(2L), eq(REFUND_ID), any()))
        .willReturn(RefundFixture.aRefundResponse(REFUND_ID));

    // when / then
    mockMvc
        .perform(
            post("/api/v1/seller/refunds/{refundId}/reject", REFUND_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRejectRequest()))
                .with(user(SELLER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void 환불거부_사유없음_400() throws Exception {
    // given — rejectReason 빈 문자열 → @Valid 에서 거부
    RefundRejectRequest emptyReason = new RefundRejectRequest("");

    // when / then
    mockMvc
        .perform(
            post("/api/v1/seller/refunds/{refundId}/reject", REFUND_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyReason))
                .with(user(SELLER)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 환불거부_미인증_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/refunds/{refundId}/reject", REFUND_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRejectRequest())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 환불거부_이미처리됨_409() throws Exception {
    // given
    willThrow(new BusinessException(RefundErrorCode.REFUND_ALREADY_PROCESSED))
        .given(refundService)
        .rejectRefund(eq(2L), eq(REFUND_ID), any());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/seller/refunds/{refundId}/reject", REFUND_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefundFixture.aRejectRequest()))
                .with(user(SELLER)))
        .andExpect(status().isConflict());
  }
}
