package com.magampick.global.security;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * refresh 토큰 쿠키 설정. application.yaml 의 auth.refresh-cookie.* 에 바인딩 (secure 는 프로필별).
 *
 * <p>{@code origins} 는 앱(Origin)별 role 매핑이다. 세 프론트(소비자/사장/관리자)가 같은 API 호스트로 요청하면 refresh 쿠키 한 개를
 * 공유해 마지막 로그인이 다른 앱의 토큰을 덮어쓴다(role 불일치 403). 이를 막기 위해 origins 가 설정된 환경(dev/prod)에서는 쿠키 이름을 role 별로
 * 분리({@code refresh_token_customer/seller/admin})하고, refresh 시 요청 Origin → role 로 해당 쿠키만 읽는다.
 * origins 가 비면(local/test) 단일 쿠키 그대로(legacy) 동작한다.
 */
@Validated
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record CookieProperties(
    @NotBlank String name,
    @NotBlank String path,
    @NotBlank String sameSite,
    boolean secure,
    Map<Role, String> origins) {

  public CookieProperties {
    origins = origins == null ? Map.of() : Map.copyOf(origins);
  }
}
