package com.magampick.phone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.global.exception.BusinessException;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import com.magampick.phone.repository.PhoneVerificationStore;
import com.magampick.phone.service.PhoneVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PhoneVerificationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PhoneVerificationStore store;
  @Autowired private PhoneVerificationService phoneVerificationService;

  @Test
  void 발송부터_검증_토큰_소비까지_실Redis로_동작한다() throws Exception {
    String rawPhone = "010-1111-1111";
    String phone = "01011111111";

    // 1) 발송
    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + rawPhone + "\"}"))
        .andExpect(status().isOk());

    // 실 Redis 에 저장된 OTP 코드를 읽어 검증에 사용 (실제 생성 코드 경로 확인)
    String code = store.findOtpCode(phone).orElseThrow();

    // 2) 검증 → 본인인증 토큰 발급
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/phone-verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"" + rawPhone + "\",\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationToken").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = objectMapper.readTree(body).path("data").path("verificationToken").asText();

    // OTP 는 소비되어 사라진다
    assertThat(store.findOtpCode(phone)).isEmpty();

    // 3) 토큰 소비 (호출 측 시뮬레이션) → 검증된 번호 반환
    assertThat(phoneVerificationService.consumeVerificationToken(token, rawPhone)).isEqualTo(phone);

    // 4) 1회용 — 같은 토큰 재사용 시 무효
    assertThatThrownBy(() -> phoneVerificationService.consumeVerificationToken(token, rawPhone))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED);
  }

  @Test
  void 즉시_재발송하면_429_쿨다운() throws Exception {
    String rawPhone = "010-2222-2222";

    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + rawPhone + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + rawPhone + "\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("OTP_RESEND_LIMIT"));
  }

  @Test
  void 잘못된_코드면_400_OTP_INVALID() throws Exception {
    String rawPhone = "010-3333-3333";

    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + rawPhone + "\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/phone-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + rawPhone + "\",\"code\":\"000000\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
  }
}
