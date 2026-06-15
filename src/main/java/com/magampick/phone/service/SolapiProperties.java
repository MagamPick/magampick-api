package com.magampick.phone.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOLAPI 설정. application.yaml 의 {@code solapi.*} 에 바인딩한다. @NotBlank 검증 제거 — 빈은 항상 생성되고 자격증명 검증은 실발송
 * 시점(SOLAPI API 응답)에 이루어진다.
 */
@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
    String apiKey, String apiSecret, String senderNumber, String apiUrl) {}
