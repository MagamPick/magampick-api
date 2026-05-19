package com.magampick.seller.controller;

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
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.seller.dto.SellerPhoneUpdateRequest;
import com.magampick.seller.dto.SellerProfileResponse;
import com.magampick.seller.dto.SellerProfileUpdateRequest;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.service.SellerService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean SellerService sellerService;
  @MockBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(1L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(2L, Role.CUSTOMER);

  private SellerProfileResponse stubProfile() {
    return new SellerProfileResponse(
        1L,
        "seller@test.com",
        "홍길동",
        "1234567890",
        "01012345678",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  // ── GET /api/v1/seller/me ──────────────────────────────────────────────────

  @Test
  void GET_seller_me_200_성공() throws Exception {
    // given
    given(sellerService.getProfile(1L)).willReturn(stubProfile());

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/me").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.ownerName").value("홍길동"));
  }

  @Test
  void GET_seller_me_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void GET_seller_me_403_고객_역할() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/me").with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void GET_seller_me_404_미존재_sellerId() throws Exception {
    // given
    given(sellerService.getProfile(1L))
        .willThrow(new BusinessException(SellerErrorCode.SELLER_NOT_FOUND));

    // when / then
    mockMvc
        .perform(get("/api/v1/seller/me").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("SELLER_NOT_FOUND"));
  }

  // ── PATCH /api/v1/seller/me ───────────────────────────────────────────────

  @Test
  void PATCH_seller_me_200_성공() throws Exception {
    // given
    SellerProfileResponse updated =
        new SellerProfileResponse(
            1L,
            "seller@test.com",
            "김철수",
            "1234567890",
            "01012345678",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    given(sellerService.updateProfile(eq(1L), any(SellerProfileUpdateRequest.class)))
        .willReturn(updated);

    // when / then
    mockMvc
        .perform(
            patch("/api/v1/seller/me")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SellerProfileUpdateRequest("김철수"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.ownerName").value("김철수"));
  }

  @Test
  void PATCH_seller_me_400_ownerName_누락() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/seller/me")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_seller_me_400_ownerName_길이_초과() throws Exception {
    // given
    String longName = "a".repeat(21);

    mockMvc
        .perform(
            patch("/api/v1/seller/me")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SellerProfileUpdateRequest(longName))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_seller_me_403_고객_역할() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/seller/me")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SellerProfileUpdateRequest("홍길동"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  // ── POST /api/v1/seller/me/phone ──────────────────────────────────────────

  @Test
  void POST_seller_me_phone_200_성공() throws Exception {
    // given
    given(sellerService.updatePhone(eq(1L), any(SellerPhoneUpdateRequest.class)))
        .willReturn(stubProfile());

    // when / then
    mockMvc
        .perform(
            post("/api/v1/seller/me/phone")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new SellerPhoneUpdateRequest("01012345678"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.phone").value("01012345678"));
  }

  @Test
  void POST_seller_me_phone_400_phone_누락() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/me/phone")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_seller_me_phone_400_phone_포맷_불일치() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/me/phone")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new SellerPhoneUpdateRequest("010-1234-5678"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_seller_me_phone_403_고객_역할() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/seller/me/phone")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new SellerPhoneUpdateRequest("01012345678"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }
}
