package com.magampick.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.magampick.notification.dto.NotificationListResponse;
import com.magampick.notification.dto.NotificationResponse;
import com.magampick.notification.dto.NotificationSettingUpdateRequest;
import com.magampick.notification.dto.SellerNotificationSettingsResponse;
import com.magampick.notification.dto.UnreadCountResponse;
import com.magampick.notification.exception.NotificationErrorCode;
import com.magampick.notification.service.SellerNotificationQueryService;
import com.magampick.notification.service.SellerNotificationSettingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SellerNotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SellerNotificationControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean SellerNotificationQueryService queryService;
  @MockitoBean SellerNotificationSettingService settingService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);

  // ── GET /api/v1/seller/notifications ─────────────────────────────────────────

  @Test
  void GET_notifications_200_목록_조회_성공() throws Exception {
    NotificationListResponse response =
        new NotificationListResponse(
            List.of(
                new NotificationResponse(
                    1L,
                    "order",
                    "새 주문",
                    "새 주문이 들어왔습니다.",
                    "2026-06-08T16:00:00+09:00",
                    false,
                    null)));
    given(queryService.list(2L)).willReturn(response);

    mockMvc
        .perform(get("/api/v1/seller/notifications").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].category").value("order"));
  }

  @Test
  void GET_notifications_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/seller/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void GET_notifications_403_소비자_역할() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/notifications").with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/seller/notifications/unread-count ────────────────────────────

  @Test
  void GET_unreadCount_200_성공() throws Exception {
    given(queryService.unreadCount(2L)).willReturn(new UnreadCountResponse(2L));

    mockMvc
        .perform(get("/api/v1/seller/notifications/unread-count").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(2));
  }

  // ── PATCH /api/v1/seller/notifications/{id}/read ──────────────────────────────

  @Test
  void PATCH_read_204_성공() throws Exception {
    willDoNothing().given(queryService).markRead(2L, 20L);

    mockMvc
        .perform(patch("/api/v1/seller/notifications/20/read").with(user(SELLER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void PATCH_read_404_알림_미존재() throws Exception {
    willThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
        .given(queryService)
        .markRead(2L, 999L);

    mockMvc
        .perform(patch("/api/v1/seller/notifications/999/read").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
  }

  // ── PATCH /api/v1/seller/notifications/read-all ──────────────────────────────

  @Test
  void PATCH_readAll_204_성공() throws Exception {
    willDoNothing().given(queryService).markAllRead(2L);

    mockMvc
        .perform(patch("/api/v1/seller/notifications/read-all").with(user(SELLER_USER)))
        .andExpect(status().isNoContent());
  }

  // ── GET /api/v1/seller/notification-settings ─────────────────────────────────

  @Test
  void GET_notificationSettings_200_성공() throws Exception {
    SellerNotificationSettingsResponse response =
        new SellerNotificationSettingsResponse(true, true, true, true, true, false);
    given(settingService.getSettings(2L)).willReturn(response);

    mockMvc
        .perform(get("/api/v1/seller/notification-settings").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.newOrder").value(true))
        .andExpect(jsonPath("$.data.marketing").value(false));
  }

  @Test
  void GET_notificationSettings_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/seller/notification-settings"))
        .andExpect(status().isUnauthorized());
  }

  // ── PATCH /api/v1/seller/notification-settings/{key} ─────────────────────────

  @Test
  void PATCH_notificationSetting_key_200_성공() throws Exception {
    SellerNotificationSettingsResponse response =
        new SellerNotificationSettingsResponse(false, true, true, true, true, false);
    given(settingService.updateSetting(eq(2L), eq("newOrder"), eq(false))).willReturn(response);

    mockMvc
        .perform(
            patch("/api/v1/seller/notification-settings/newOrder")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new NotificationSettingUpdateRequest(false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.newOrder").value(false));
  }

  @Test
  void PATCH_notificationSetting_key_400_잘못된_키() throws Exception {
    given(settingService.updateSetting(eq(2L), eq("badKey"), eq(true)))
        .willThrow(new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_SETTING_KEY));

    mockMvc
        .perform(
            patch("/api/v1/seller/notification-settings/badKey")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new NotificationSettingUpdateRequest(true))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_NOTIFICATION_SETTING_KEY"));
  }

  @Test
  void PATCH_notificationSetting_key_400_enabled_누락() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/seller/notification-settings/newOrder")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_notificationSetting_key_403_소비자_역할() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/seller/notification-settings/newOrder")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new NotificationSettingUpdateRequest(true))))
        .andExpect(status().isForbidden());
  }
}
