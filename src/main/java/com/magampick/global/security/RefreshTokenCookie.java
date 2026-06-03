package com.magampick.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * refresh 토큰 HttpOnly 쿠키 생성/삭제/읽기. 토글(persistent)에 따라 max-age(refresh TTL) vs 세션 쿠키. 속성(secure /
 * sameSite / path)은 {@link CookieProperties}(프로필별)에서 온다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

  private final CookieProperties properties;
  private final JwtProvider jwtProvider;

  public ResponseCookie create(String refreshToken, boolean persistent) {
    ResponseCookie.ResponseCookieBuilder builder = baseBuilder(refreshToken);
    if (persistent) {
      builder.maxAge(jwtProvider.refreshTokenExpiresInSeconds());
    }
    return builder.build();
  }

  public ResponseCookie clear() {
    return baseBuilder("").maxAge(0).build();
  }

  public Optional<String> read(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> properties.name().equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
    return ResponseCookie.from(properties.name(), value)
        .httpOnly(true)
        .secure(properties.secure())
        .path(properties.path())
        .sameSite(properties.sameSite());
  }
}
