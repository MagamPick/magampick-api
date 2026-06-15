package com.magampick.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * refresh 토큰 HttpOnly 쿠키 생성/삭제/읽기. 토글(persistent)에 따라 max-age(refresh TTL) vs 세션 쿠키. 속성(secure /
 * sameSite / path)은 {@link CookieProperties}(프로필별)에서 온다.
 *
 * <p>origins 가 설정된 환경에서는 쿠키 이름을 role 별로 분리({@code refresh_token_customer/seller/admin})해 세 앱이 같은
 * API 호스트에서 refresh 쿠키를 덮어쓰지 않도록 한다. refresh 시에는 요청 Origin → role 로 해당 앱의 쿠키만 읽는다. origins 가 비면
 * (local/test) 단일 쿠키 그대로(legacy) 동작한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

  private final CookieProperties properties;
  private final JwtProvider jwtProvider;

  /** role 별 쿠키 생성. origins 미설정(legacy)이면 role 무관하게 단일 base 이름을 쓴다. */
  public ResponseCookie create(String refreshToken, boolean persistent, Role role) {
    ResponseCookie.ResponseCookieBuilder builder = baseBuilder(nameFor(role), refreshToken);
    if (persistent) {
      builder.maxAge(jwtProvider.refreshTokenExpiresInSeconds());
    }
    return builder.build();
  }

  /** 요청 Origin 으로 role 을 판별해 해당 앱의 쿠키만 만료(maxAge=0)시킨다. legacy 면 base 쿠키. */
  public ResponseCookie clearFor(HttpServletRequest request) {
    String name = resolveClientRole(request).map(this::nameFor).orElse(properties.name());
    return baseBuilder(name, "").maxAge(0).build();
  }

  /**
   * refresh 용 읽기 — split 이면 Origin→role 쿠키, legacy 면 base 쿠키.
   *
   * <p>Origin 이 맵에 없는 경우(localhost 개발 등)는 role 별 쿠키를 순차 시도해 첫 번째 유효한 것을 반환한다. 같은 브라우저에서 여러 role 로
   * 동시 로그인한 상황은 개발 환경에서만 발생하므로 보안 위협이 아니다.
   */
  public Optional<String> readForRefresh(HttpServletRequest request) {
    if (properties.origins().isEmpty()) {
      return readByName(request, properties.name());
    }
    Optional<Role> role = resolveClientRole(request);
    if (role.isPresent()) {
      return role.flatMap(r -> readByName(request, nameFor(r)));
    }
    // Origin 미매칭(localhost 개발 환경 등) — role별 쿠키 순차 시도
    return Arrays.stream(Role.values())
        .map(r -> readByName(request, nameFor(r)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  /** 명시적 role 읽기 — 인증된 주체의 role 을 아는 경우(비밀번호 변경 등). */
  public Optional<String> read(HttpServletRequest request, Role role) {
    return readByName(request, nameFor(role));
  }

  /** 요청 Origin → role. origins 미설정(legacy)이거나 Origin 미매칭이면 empty. */
  public Optional<Role> resolveClientRole(HttpServletRequest request) {
    if (properties.origins().isEmpty()) {
      return Optional.empty();
    }
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin == null) {
      return Optional.empty();
    }
    return properties.origins().entrySet().stream()
        .filter(entry -> entry.getValue().equalsIgnoreCase(origin))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  /** role 별 쿠키 이름. origins 미설정이면 base 그대로(legacy 호환). */
  private String nameFor(Role role) {
    if (properties.origins().isEmpty()) {
      return properties.name();
    }
    return properties.name() + "_" + role.name().toLowerCase(Locale.ROOT);
  }

  private Optional<String> readByName(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return Arrays.stream(request.getCookies())
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  private ResponseCookie.ResponseCookieBuilder baseBuilder(String name, String value) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(properties.secure())
        .path(properties.path())
        .sameSite(properties.sameSite());
  }
}
