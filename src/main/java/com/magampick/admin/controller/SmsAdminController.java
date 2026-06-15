package com.magampick.admin.controller;

import com.magampick.phone.service.SmsConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SMS mock 모드 런타임 토글 (ROLE_ADMIN 전용). 재시작 없이 mock ↔ 실발송 전환. */
@Tag(name = "Admin - SMS", description = "SMS mock 토글 (ROLE_ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/admin/sms")
@RequiredArgsConstructor
public class SmsAdminController {

  private final SmsConfig smsConfig;

  @Operation(
      summary = "SMS mock 토글",
      description = "enabled=true: mock(000000 우회), false: SOLAPI 실발송. mock=false 로 시작한 서버만 전환 가능.")
  @PostMapping("/mock")
  public void setMockEnabled(@RequestParam boolean enabled) {
    smsConfig.setMockEnabled(enabled);
  }
}
