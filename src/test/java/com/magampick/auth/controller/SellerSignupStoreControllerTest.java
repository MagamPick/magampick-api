package com.magampick.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.SecurityConfig;
import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.StoreService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerSignupStoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerSignupStoreControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean StoreService storeService;
  @MockitoBean JwtProvider jwtProvider;

  private String validRequestJson() throws Exception {
    return objectMapper.writeValueAsString(
        new BusinessVerificationRequest("123-45-67890", "홍길동", LocalDate.of(2024, 3, 15)));
  }

  private String invalidRequestJson() throws Exception {
    return objectMapper.writeValueAsString(
        new BusinessVerificationRequest("123-45-67890", "", LocalDate.of(2024, 3, 15)));
  }

  @Test
  void 사장_가입용_사업자_진위확인_비회원_성공() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isNoContent());
  }

  @Test
  void 사장_가입용_사업자_진위확인_입력값_검증_실패() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 사장_가입용_사업자_진위확인_불일치() throws Exception {
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH))
        .given(storeService)
        .verifyBusiness(any());

    mockMvc
        .perform(
            post("/api/v1/auth/seller/stores/business-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BUSINESS_INFO_MISMATCH"));
  }
}
