package com.magampick.phone.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magampick.global.exception.BusinessException;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.service.PhoneVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PhoneVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class PhoneVerificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PhoneVerificationService phoneVerificationService;

  @Test
  void 인증번호_발송_성공_200() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"010-1234-5678\"}"))
        .andExpect(status().isOk());
    verify(phoneVerificationService).requestCode("010-1234-5678");
  }

  @Test
  void 인증번호_검증_성공_시_토큰_반환_200() throws Exception {
    // given
    given(phoneVerificationService.verifyCode("010-1234-5678", "123456")).willReturn("token-xyz");

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"010-1234-5678\",\"code\":\"123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.verificationToken").value("token-xyz"));
  }

  @Test
  void 휴대폰_형식_위반_시_400_PHONE_FORMAT_INVALID() throws Exception {
    // given
    willThrow(new BusinessException(PhoneVerificationErrorCode.PHONE_FORMAT_INVALID))
        .given(phoneVerificationService)
        .requestCode(anyString());

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"010-12\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("PHONE_FORMAT_INVALID"));
  }

  @Test
  void 재발송_제한_시_429_OTP_RESEND_LIMIT() throws Exception {
    // given
    willThrow(new BusinessException(PhoneVerificationErrorCode.OTP_RESEND_LIMIT))
        .given(phoneVerificationService)
        .requestCode(anyString());

    // when & then
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"010-1234-5678\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("OTP_RESEND_LIMIT"));
  }

  @Test
  void 빈_휴대폰_번호_400_INVALID_INPUT() throws Exception {
    // when & then — DTO @NotBlank 위반은 Bean Validation 으로 INVALID_INPUT
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }
}
