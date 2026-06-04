package com.magampick.auth.oauth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 카카오 OAuth 설정. application.yaml 의 {@code oauth.kakao.*} 에 바인딩한다 (client-id/secret 은 환경변수).
 * dev/prod 프로필({@link KakaoOAuthConfig})에서만 등록되므로 local/test 에서는 검증되지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOAuthProperties(
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotBlank String tokenUri,
    @NotBlank String userInfoUri) {}
