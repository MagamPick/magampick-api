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
import com.magampick.notification.dto.CustomerNotificationSettingsResponse;
import com.magampick.notification.dto.NotificationListResponse;
import com.magampick.notification.dto.NotificationResponse;
import com.magampick.notification.dto.NotificationSettingUpdateRequest;
import com.magampick.notification.dto.UnreadCountResponse;
import com.magampick.notification.exception.NotificationErrorCode;
import com.magampick.notification.service.CustomerNotificationQueryService;
import com.magampick.notification.service.CustomerNotificationSettingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerNotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CustomerNotificationControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean CustomerNotificationQueryService queryService;
  @MockitoBean CustomerNotificationSettingService settingService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(1L, Role.CUSTOMER);
  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(2L, Role.SELLER);

  // ── GET /api/v1/customers/me/notifications ────────────────────────────────────

  @Test
  void GET_notifications_200_전체_목록_조회() throws Exception {
    NotificationListResponse response =
        new NotificationListResponse(
            List.of(
                new NotificationResponse(
                    1L,
                    "order",
                    "주문 완료",
                    "주문이 접수되었습니다.",
                    "2026-06-08T16:00:00+09:00",
                    false,
                    null)));
    given(queryService.list(1L, null)).willReturn(response);

    mockMvc
        .perform(get("/api/v1/customers/me/notifications").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items[0].category").value("order"));
  }

  @Test
  void GET_notifications_200_deal_세그먼트_조회() throws Exception {
    NotificationListResponse response = new NotificationListResponse(List.of());
    given(queryService.list(1L, "deal")).willReturn(response);

    mockMvc
        .perform(
            get("/api/v1/customers/me/notifications")
                .param("segment", "deal")
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }

  @Test
  void GET_notifications_401_미인증() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void GET_notifications_403_사장_역할() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/notifications").with(user(SELLER_USER)))
        .andExpect(status().isForbidden());
  }

  // ── GET /api/v1/customers/me/notifications/unread-count ───────────────────────

  @Test
  void GET_unreadCount_200_성공() throws Exception {
    given(queryService.unreadCount(1L)).willReturn(new UnreadCountResponse(3L));

    mockMvc
        .perform(get("/api/v1/customers/me/notifications/unread-count").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(3));
  }

  @Test
  void GET_unreadCount_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/notifications/unread-count"))
        .andExpect(status().isUnauthorized());
  }

  // ── PATCH /api/v1/customers/me/notifications/{id}/read ────────────────────────

  @Test
  void PATCH_read_204_성공() throws Exception {
    willDoNothing().given(queryService).markRead(1L, 10L);

    mockMvc
        .perform(patch("/api/v1/customers/me/notifications/10/read").with(user(CUSTOMER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void PATCH_read_404_알림_미존재() throws Exception {
    willThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
        .given(queryService)
        .markRead(1L, 999L);

    mockMvc
        .perform(patch("/api/v1/customers/me/notifications/999/read").with(user(CUSTOMER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
  }

  @Test
  void PATCH_read_401_미인증() throws Exception {
    mockMvc
        .perform(patch("/api/v1/customers/me/notifications/10/read"))
        .andExpect(status().isUnauthorized());
  }

  // ── PATCH /api/v1/customers/me/notifications/read-all ────────────────────────

  @Test
  void PATCH_readAll_204_성공() throws Exception {
    willDoNothing().given(queryService).markAllRead(1L);

    mockMvc
        .perform(patch("/api/v1/customers/me/notifications/read-all").with(user(CUSTOMER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void PATCH_readAll_401_미인증() throws Exception {
    mockMvc
        .perform(patch("/api/v1/customers/me/notifications/read-all"))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/v1/customers/me/notification-settings ───────────────────────────

  @Test
  void GET_notificationSettings_200_성공() throws Exception {
    CustomerNotificationSettingsResponse response =
        new CustomerNotificationSettingsResponse(true, true, true, true, false, false);
    given(settingService.getSettings(1L)).willReturn(response);

    mockMvc
        .perform(get("/api/v1/customers/me/notification-settings").with(user(CUSTOMER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nearbyDeal").value(true))
        .andExpect(jsonPath("$.data.marketing").value(false));
  }

  @Test
  void GET_notificationSettings_401_미인증() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers/me/notification-settings"))
        .andExpect(status().isUnauthorized());
  }

  // ── PATCH /api/v1/customers/me/notification-settings/{key} ───────────────────

  @Test
  void PATCH_notificationSetting_key_200_성공() throws Exception {
    CustomerNotificationSettingsResponse response =
        new CustomerNotificationSettingsResponse(false, true, true, true, false, false);
    given(settingService.updateSetting(eq(1L), eq("nearbyDeal"), eq(false))).willReturn(response);

    mockMvc
        .perform(
            patch("/api/v1/customers/me/notification-settings/nearbyDeal")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new NotificationSettingUpdateRequest(false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nearbyDeal").value(false));
  }

  @Test
  void PATCH_notificationSetting_key_400_잘못된_키() throws Exception {
    given(settingService.updateSetting(eq(1L), eq("badKey"), eq(true)))
        .willThrow(new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_SETTING_KEY));

    mockMvc
        .perform(
            patch("/api/v1/customers/me/notification-settings/badKey")
                .with(user(CUSTOMER_USER))
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
            patch("/api/v1/customers/me/notification-settings/nearbyDeal")
                .with(user(CUSTOMER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void PATCH_notificationSetting_key_401_미인증() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/customers/me/notification-settings/nearbyDeal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new NotificationSettingUpdateRequest(true))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void PATCH_notificationSetting_key_403_사장_역할() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/customers/me/notification-settings/nearbyDeal")
                .with(user(SELLER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new NotificationSettingUpdateRequest(true))))
        .andExpect(status().isForbidden());
  }
}
