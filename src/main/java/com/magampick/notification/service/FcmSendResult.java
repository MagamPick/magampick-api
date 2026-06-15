package com.magampick.notification.service;

import java.util.List;

/** FCM 멀티캐스트 발송 결과. {@code deadTokens} = UNREGISTERED/INVALID_ARGUMENT 로 실패해 정리 대상인 토큰. */
public record FcmSendResult(int successCount, List<String> deadTokens) {}
