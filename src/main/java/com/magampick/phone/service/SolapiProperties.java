package com.magampick.phone.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SOLAPI 설정. application.yaml 의 {@code solapi.*} 에 바인딩한다 (api-key/secret/sender-number 는 환경변수). 실발송
 * 구성({@link SolapiConfig}, {@code @Profile("!test")})에서만 등록되므로 test 프로파일에서는 검증되지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
    @NotBlank String apiKey,
    @NotBlank String apiSecret,
    @NotBlank String senderNumber,
    @NotBlank String apiUrl) {}
