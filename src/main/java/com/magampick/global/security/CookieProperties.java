package com.magampick.global.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** refresh 토큰 쿠키 설정. application.yaml 의 auth.refresh-cookie.* 에 바인딩 (secure 는 프로필별). */
@Validated
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record CookieProperties(
    @NotBlank String name, @NotBlank String path, @NotBlank String sameSite, boolean secure) {}
