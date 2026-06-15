package com.magampick.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.magampick.notification.dto.DevPushEchoRequest;
import com.magampick.notification.dto.DevPushMeRequest;
import com.magampick.notification.service.FcmSender;
import com.magampick.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DevPushController.class)
@ActiveProfiles("local") // DevPushController 는 @Profile({"local","dev"}) — local 활성화해야 로드됨
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class DevPushControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean FcmSender fcmSender;
  @MockitoBean NotificationService notificationService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── POST /api/v1/dev/push/echo ───────────────────────────────────────────────

  @Test
  void POST_echo_200_발송_성공() throws Exception {
    given(fcmSender.send(any(), any())).willReturn("projects/x/messages/1");

    mockMvc
        .perform(
            post("/api/v1/dev/push/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DevPushEchoRequest("tok", "제목", "본문")))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.messageId").value("projects/x/messages/1"));
  }

  @Test
  void POST_echo_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/dev/push/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new DevPushEchoRequest("tok", "제목", "본문"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_echo_400_token_누락() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/dev/push/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목\",\"body\":\"본문\"}")
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  // ── POST /api/v1/dev/push/me ─────────────────────────────────────────────────

  @Test
  void POST_me_200_내_토큰으로_발송() throws Exception {
    given(
            notificationService.sendToOwner(
                eq(Role.CUSTOMER), eq(1L), any(), any(), any(), any(), any()))
        .willReturn(2);

    mockMvc
        .perform(
            post("/api/v1/dev/push/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DevPushMeRequest("제목", "본문")))
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sentCount").value(2));
  }

  @Test
  void POST_me_401_미인증() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/dev/push/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DevPushMeRequest("제목", "본문"))))
        .andExpect(status().isUnauthorized());
  }
}
