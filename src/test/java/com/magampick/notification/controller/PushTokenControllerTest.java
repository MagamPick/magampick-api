package com.magampick.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.magampick.notification.dto.PushTokenDeleteRequest;
import com.magampick.notification.dto.PushTokenRegisterRequest;
import com.magampick.notification.dto.PushTokenResponse;
import com.magampick.notification.service.PushTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushTokenController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PushTokenControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean PushTokenService pushTokenService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  // ── POST /api/v1/push-tokens ─────────────────────────────────────────────────

  @Test
  void POST_pushTokens_201_소비자_등록_성공() throws Exception {
    given(pushTokenService.register(eq(Role.CUSTOMER), eq(1L), eq("tok")))
        .willReturn(new PushTokenResponse(100L));

    mockMvc
        .perform(
            post("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PushTokenRegisterRequest("tok")))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(100));
  }

  @Test
  void POST_pushTokens_201_사장_등록_성공() throws Exception {
    given(pushTokenService.register(eq(Role.SELLER), eq(2L), eq("tok")))
        .willReturn(new PushTokenResponse(101L));

    mockMvc
        .perform(
            post("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PushTokenRegisterRequest("tok")))
                .with(user(SELLER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(101));
  }

  @Test
  void POST_pushTokens_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PushTokenRegisterRequest("tok"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_pushTokens_400_token_누락() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  // ── DELETE /api/v1/push-tokens ───────────────────────────────────────────────

  @Test
  void DELETE_pushTokens_204_해제_성공() throws Exception {
    willDoNothing().given(pushTokenService).unregister("tok");

    mockMvc
        .perform(
            delete("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PushTokenDeleteRequest("tok")))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void DELETE_pushTokens_401_미인증() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PushTokenDeleteRequest("tok"))))
        .andExpect(status().isUnauthorized());
  }
}
