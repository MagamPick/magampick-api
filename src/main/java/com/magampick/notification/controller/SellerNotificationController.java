package com.magampick.notification.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.notification.dto.NotificationListResponse;
import com.magampick.notification.dto.NotificationSettingUpdateRequest;
import com.magampick.notification.dto.SellerNotificationSettingsResponse;
import com.magampick.notification.dto.UnreadCountResponse;
import com.magampick.notification.service.SellerNotificationQueryService;
import com.magampick.notification.service.SellerNotificationSettingService;
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
import org.springframework.web.bind.annotation.RestController;

/** 사장 알림 수신함 및 알림 설정 API. */
@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
@Tag(name = "Seller Notification", description = "사장 알림 수신함 및 알림 설정 API")
public class SellerNotificationController {

  private final SellerNotificationQueryService queryService;
  private final SellerNotificationSettingService settingService;

  @GetMapping("/notifications")
  @Operation(summary = "사장 알림 목록 조회")
  public NotificationListResponse list(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return queryService.list(userDetails.getUserId());
  }

  @GetMapping("/notifications/unread-count")
  @Operation(summary = "사장 미읽음 알림 수 조회")
  public UnreadCountResponse unreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return queryService.unreadCount(userDetails.getUserId());
  }

  @PatchMapping("/notifications/{id}/read")
  @Operation(summary = "사장 알림 단건 읽음 처리")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long id) {
    queryService.markRead(userDetails.getUserId(), id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/notifications/read-all")
  @Operation(summary = "사장 알림 전체 읽음 처리")
  public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
    queryService.markAllRead(userDetails.getUserId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/notification-settings")
  @Operation(summary = "사장 알림 설정 조회")
  public SellerNotificationSettingsResponse getSettings(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return settingService.getSettings(userDetails.getUserId());
  }

  @PatchMapping("/notification-settings/{key}")
  @Operation(
      summary = "사장 알림 설정 개별 변경",
      description = "key: newOrder, orderCancel, refundRequest, newReview, notice, marketing")
  public SellerNotificationSettingsResponse updateSetting(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable String key,
      @Valid @RequestBody NotificationSettingUpdateRequest request) {
    return settingService.updateSetting(userDetails.getUserId(), key, request.enabled());
  }
}
