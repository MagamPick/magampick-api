package com.magampick.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshTokenCookieTest {

  private static final String CUSTOMER_ORIGIN = "https://dev.magampick.com";
  private static final String SELLER_ORIGIN = "https://owner.dev.magampick.com";
  private static final String ADMIN_ORIGIN = "https://admin.dev.magampick.com";

  // refreshTokenExpiresInSeconds() 만 쓰며(maxAge), 기본 반환 0L 로 충분 — 이름 검증만 한다.
  private final JwtProvider jwtProvider = mock(JwtProvider.class);

  private CookieProperties splitProperties() {
    return new CookieProperties(
        "refresh_token",
        "/api/v1/auth",
        "None",
        true,
        Map.of(
            Role.CUSTOMER, CUSTOMER_ORIGIN,
            Role.SELLER, SELLER_ORIGIN,
            Role.ADMIN, ADMIN_ORIGIN));
  }

  private CookieProperties legacyProperties() {
    return new CookieProperties("refresh_token", "/api/v1/auth", "Lax", false, Map.of());
  }

  private MockHttpServletRequest requestWith(String origin, Cookie... cookies) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (origin != null) {
      request.addHeader(HttpHeaders.ORIGIN, origin);
    }
    if (cookies.length > 0) {
      request.setCookies(cookies);
    }
    return request;
  }

  @Nested
  class Split_모드 {

    private final RefreshTokenCookie cookie =
        new RefreshTokenCookie(splitProperties(), jwtProvider);

    @Test
    void 생성시_role별_쿠키이름으로_분리한다() {
      ResponseCookie customer = cookie.create("rawC", true, Role.CUSTOMER);
      ResponseCookie seller = cookie.create("rawS", true, Role.SELLER);
      ResponseCookie admin = cookie.create("rawA", true, Role.ADMIN);

      assertThat(customer.getName()).isEqualTo("refresh_token_customer");
      assertThat(seller.getName()).isEqualTo("refresh_token_seller");
      assertThat(admin.getName()).isEqualTo("refresh_token_admin");
    }

    @Test
    void 요청_Origin으로_role을_판별한다() {
      assertThat(cookie.resolveClientRole(requestWith(CUSTOMER_ORIGIN))).contains(Role.CUSTOMER);
      assertThat(cookie.resolveClientRole(requestWith(SELLER_ORIGIN))).contains(Role.SELLER);
      assertThat(cookie.resolveClientRole(requestWith(ADMIN_ORIGIN))).contains(Role.ADMIN);
    }

    @Test
    void Origin이_없거나_미등록이면_role_판별_불가() {
      assertThat(cookie.resolveClientRole(requestWith(null))).isEmpty();
      assertThat(cookie.resolveClientRole(requestWith("https://evil.example.com"))).isEmpty();
    }

    @Test
    void refresh읽기는_여러_쿠키중_Origin에_맞는_것만_읽는다() {
      MockHttpServletRequest request =
          requestWith(
              CUSTOMER_ORIGIN,
              new Cookie("refresh_token_customer", "customerToken"),
              new Cookie("refresh_token_admin", "adminToken"));

      assertThat(cookie.readForRefresh(request)).contains("customerToken");
    }

    @Test
    void 소비자앱이_관리자쿠키만_있으면_토큰을_받지않는다() {
      // 버그 재현 방지: admin 으로 로그인한 브라우저에서 소비자앱(Origin)이 refresh 해도 admin 토큰이 새지 않아야 한다.
      MockHttpServletRequest request =
          requestWith(CUSTOMER_ORIGIN, new Cookie("refresh_token_admin", "adminToken"));

      assertThat(cookie.readForRefresh(request)).isEmpty();
    }

    @Test
    void clearFor는_Origin에_해당하는_쿠키만_만료시킨다() {
      ResponseCookie cleared = cookie.clearFor(requestWith(SELLER_ORIGIN));

      assertThat(cleared.getName()).isEqualTo("refresh_token_seller");
      assertThat(cleared.getMaxAge().isZero()).isTrue();
    }
  }

  @Nested
  class Legacy_모드 {

    private final RefreshTokenCookie cookie =
        new RefreshTokenCookie(legacyProperties(), jwtProvider);

    @Test
    void origins_미설정이면_단일_base_이름을_쓴다() {
      ResponseCookie created = cookie.create("rawS", true, Role.SELLER);

      assertThat(created.getName()).isEqualTo("refresh_token");
    }

    @Test
    void refresh읽기는_Origin_무관하게_base_쿠키를_읽는다() {
      MockHttpServletRequest request =
          requestWith(null, new Cookie("refresh_token", "legacyToken"));

      Optional<String> token = cookie.readForRefresh(request);

      assertThat(token).contains("legacyToken");
    }
  }
}
