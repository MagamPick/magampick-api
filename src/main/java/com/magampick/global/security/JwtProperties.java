package com.magampick.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT 설정. application.yaml 의 jwt.* 프로퍼티에 바인딩된다 (auth.md §12). */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @Positive long accessTokenValidityMinutes,
    @Positive long refreshTokenValidityDays) {}
