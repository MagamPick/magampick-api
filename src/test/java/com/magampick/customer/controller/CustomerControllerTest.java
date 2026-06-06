package com.magampick.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.customer.dto.CustomerPhoneUpdateRequest;
import com.magampick.customer.dto.CustomerProfileResponse;
import com.magampick.customer.dto.CustomerProfileUpdateRequest;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.service.CustomerService;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.phone.exception.PhoneVerificationErrorCode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CustomerControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean CustomerService customerService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  private CustomerProfileResponse stubProfile() {
    return new CustomerProfileResponse(
        1L,
        "customer@test.com",
        "마감픽유저",
        "01012345678",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  // ── GET /api/v1/customers/me ───────────────────────────────────────────────

  @Test
  void GET_customers_me_200_성공() throws Exception {
    // given
    given(customerService.getProfile(1L)).willReturn(stubProfile());

    // when / then
    mockMvc
        .perform(get("/api/v1/customers/me").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.nickname").value("마감픽유저"))
        .andExpect(jsonPath("$.data.email").value("customer@test.com"));
  }

  @Test
  void GET_customers_me_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void GET_customers_me_403_사장_역할() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void GET_customers_me_404_미존재_customerId() throws Exception {
    // given
    given(customerService.getProfile(1L))
        .willThrow(new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

    // when / then
    mockMvc
        .perform(get("/api/v1/customers/me").with(user(CUSTOMER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  // ── PATCH /api/v1/customers/me ────────────────────────────────────────────

  @Test
  void PATCH_customers_me_200_성공() throws Exception {
    // given
    CustomerProfileResponse updated =
        new CustomerProfileResponse(
            1L,
            "customer@test.com",
            "새닉네임",
            "01012345678",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    given(customerService.updateProfile(eq(1L), any(CustomerProfileUpdateRequest.class)))
        .willReturn(updated);

    // when / then
    mockMvc
        .perform(
            patch("/api/v1/customers/me")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CustomerProfileUpdateRequest("새닉네임"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
  }

  @Test
  void PATCH_customers_me_400_nickname_누락() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/customers/me")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_customers_me_400_nickname_길이_초과() throws Exception {
    // given
    String longNickname = "a".repeat(21);

    mockMvc
        .perform(
            patch("/api/v1/customers/me")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerProfileUpdateRequest(longNickname))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_customers_me_403_사장_역할() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/customers/me")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CustomerProfileUpdateRequest("새닉네임"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // ── POST /api/v1/customers/me/phone ───────────────────────────────────────

  @Test
  void POST_customers_me_phone_200_성공() throws Exception {
    // given
    given(customerService.updatePhone(eq(1L), any(CustomerPhoneUpdateRequest.class)))
        .willReturn(stubProfile());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/customers/me/phone")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerPhoneUpdateRequest("01012345678", "valid-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.phone").value("01012345678"));
  }

  @Test
  void POST_customers_me_phone_400_phone_누락() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/phone")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"verificationToken\":\"some-token\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_customers_me_phone_400_verificationToken_누락() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/phone")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"01012345678\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_customers_me_phone_400_phone_포맷_불일치() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/phone")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerPhoneUpdateRequest("010-1234-5678", "some-token"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_customers_me_phone_400_본인인증_토큰_만료() throws Exception {
    // given
    given(customerService.updatePhone(eq(1L), any(CustomerPhoneUpdateRequest.class)))
        .willThrow(new BusinessException(PhoneVerificationErrorCode.PHONE_VERIFICATION_EXPIRED));

    // when / then
    mockMvc
        .perform(
            post("/api/v1/customers/me/phone")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerPhoneUpdateRequest("01012345678", "expired-token"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("PHONE_VERIFICATION_EXPIRED"));
  }

  @Test
  void POST_customers_me_phone_403_사장_역할() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/customers/me/phone")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerPhoneUpdateRequest("01012345678", "some-token"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
