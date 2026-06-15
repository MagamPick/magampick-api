package com.magampick.notification.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.dto.DevPushEchoRequest;
import com.magampick.notification.dto.DevPushMeRequest;
import com.magampick.notification.dto.DevPushMeResponse;
import com.magampick.notification.dto.DevPushResponse;
import com.magampick.notification.service.FcmSender;
import com.magampick.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FCM 발송 테스트용 임시 컨트롤러 — local/dev 프로파일 한정. 출시 전 제거.
 *
 * <p>테스트 사다리: (1) {@code /echo} 토큰 직접 발송 → Firebase 양방향 배선 검증. (2) {@code /me} 내 저장 토큰으로 발송 →
 * 저장+조회+발송 경로 검증.
 */
@RestController
@RequestMapping("/api/v1/dev/push")
@RequiredArgsConstructor
@Profile({"local", "dev"})
@Tag(name = "Dev Push (임시)", description = "FCM 발송 테스트용 임시 엔드포인트 — local/dev 한정, 출시 전 제거")
public class DevPushController {

  private final FcmSender fcmSender;
  private final NotificationService notificationService;

  @PostMapping("/echo")
  @Operation(
      summary = "[임시] 토큰 직접 발송",
      description = "body 의 token 으로 FCM 1건 발송. 저장 없이 Firebase 배선 검증용.")
  public DevPushResponse echo(@RequestBody @Valid DevPushEchoRequest request) {
    String messageId =
        fcmSender.send(
            request.token(),
            FcmSender.dataOf(
                request.title(), request.body(), NotificationCategory.SYSTEM, null, null));
    return new DevPushResponse(messageId);
  }

  @PostMapping("/me")
  @Operation(summary = "[임시] 내 토큰으로 발송", description = "인증 사용자의 저장된 모든 토큰으로 발송. 저장+조회+발송 경로 검증용.")
  public DevPushMeResponse me(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestBody @Valid DevPushMeRequest request) {
    int sentCount =
        notificationService.sendToOwner(
            userDetails.getRole(),
            userDetails.getUserId(),
            NotificationCategory.SYSTEM,
            request.title(),
            request.body(),
            null,
            null);
    return new DevPushMeResponse(sentCount);
  }
}
