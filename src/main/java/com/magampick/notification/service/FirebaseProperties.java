package com.magampick.notification.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * FCM 설정. {@code fcm.credentials} = Firebase 서비스 계정 JSON 을 base64 인코딩한 값(환경변수 FCM_CREDENTIALS). 실발송
 * 구성({@link FirebaseConfig}, {@code app.fcm.mock-enabled=false})에서만 등록되므로 mock
 * 모드(local/test/dev)에서는 검증되지 않는다 — 자격증명 없이 기동 가능.
 */
@Validated
@ConfigurationProperties(prefix = "fcm")
public record FirebaseProperties(@NotBlank String credentials) {}
