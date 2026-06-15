package com.magampick.notification.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.notification.dto.CustomerNotificationSettingsResponse;
import com.magampick.notification.dto.NotificationListResponse;
import com.magampick.notification.dto.NotificationSettingUpdateRequest;
import com.magampick.notification.dto.UnreadCountResponse;
import com.magampick.notification.service.CustomerNotificationQueryService;
import com.magampick.notification.service.CustomerNotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 소비자 알림 수신함 및 알림 설정 API. */
@RestController
@RequestMapping("/api/v1/customers/me")
@RequiredArgsConstructor
@Tag(name = "Customer Notification", description = "소비자 알림 수신함 및 알림 설정 API")
public class CustomerNotificationController {

  private final CustomerNotificationQueryService queryService;
  private final CustomerNotificationSettingService settingService;

  @GetMapping("/notifications")
  @Operation(
      summary = "소비자 알림 목록 조회",
      description = "segment: all(기본)/deal/order. null 또는 빈 값이면 전체 조회.")
  public NotificationListResponse list(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(required = false) String segment) {
    return queryService.list(userDetails.getUserId(), segment);
  }

  @GetMapping("/notifications/unread-count")
  @Operation(summary = "소비자 미읽음 알림 수 조회")
  public UnreadCountResponse unreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return queryService.unreadCount(userDetails.getUserId());
  }

  @PatchMapping("/notifications/{id}/read")
  @Operation(summary = "소비자 알림 단건 읽음 처리")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    queryService.markRead(userDetails.getUserId(), id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/notifications/read-all")
  @Operation(summary = "소비자 알림 전체 읽음 처리")
  public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
    queryService.markAllRead(userDetails.getUserId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/notification-settings")
  @Operation(summary = "소비자 알림 설정 조회")
  public CustomerNotificationSettingsResponse getSettings(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return settingService.getSettings(userDetails.getUserId());
  }

  @PatchMapping("/notification-settings/{key}")
  @Operation(
      summary = "소비자 알림 설정 개별 변경",
      description =
          "key: nearbyDeal, favoriteStore, orderRefund, reviewReply, eventBenefit, marketing")
  public CustomerNotificationSettingsResponse updateSetting(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable String key,
      @Valid @RequestBody NotificationSettingUpdateRequest request) {
    return settingService.updateSetting(userDetails.getUserId(), key, request.enabled());
  }
}
