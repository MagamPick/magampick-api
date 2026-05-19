package com.magampick.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.auth.dto.CustomerSignupRequest;
import com.magampick.auth.dto.KakaoLoginRequest;
import com.magampick.auth.dto.LoginRequest;
import com.magampick.auth.dto.RefreshTokenRequest;
import com.magampick.auth.dto.SellerSignupRequest;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.service.AuthService;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockitoBean AuthService authService;

  @Test
  void 소비자_회원가입_성공시_201() throws Exception {
    given(authService.signupCustomer(any())).willReturn(new TokenResponse("a", "r", 1800L));

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CustomerSignupRequest("c@test.com", "Abcd1234!", "nick"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("a"));
  }

  @Test
  void 소비자_회원가입_검증_실패시_400() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 사장_로그인_성공시_200() throws Exception {
    given(authService.loginSeller(any())).willReturn(new TokenResponse("a", "r", 1800L));

    mockMvc
        .perform(
            post("/api/v1/auth/seller/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest("s@test.com", "Abcd1234!"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.refreshToken").value("r"));
  }

  @Test
  void 카카오_mock_로그인_성공시_200() throws Exception {
    given(authService.kakaoLogin(any())).willReturn(new TokenResponse("a", "r", 1800L));

    mockMvc
        .perform(
            post("/api/v1/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new KakaoLoginRequest("token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void 토큰_갱신_성공시_200() throws Exception {
    given(authService.refresh(any())).willReturn(new TokenResponse("newA", "newR", 1800L));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("r"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("newA"));
  }

  @Test
  void 로그아웃_성공시_204() throws Exception {
    CustomUserDetails principal = new CustomUserDetails(1L, Role.CUSTOMER);
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void 사장_회원가입_검증_실패시_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/seller/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void 사장_회원가입_성공시_201() throws Exception {
    given(authService.signupSeller(any())).willReturn(new TokenResponse("a", "r", 1800L));

    mockMvc
        .perform(
            post("/api/v1/auth/seller/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new SellerSignupRequest("s@test.com", "Abcd1234!", "owner", "1234567890"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }
}
