package com.magampick.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;

/**
 * dev/prod 프로파일의 {@code auth.refresh-cookie.origins} 는 키가 Role enum 이다. 이 바인딩은 test 프로파일(origins
 * 미설정)에선 안 거쳐 배포 시 처음 실행되므로, enum 키 변환을 미리 검증해 기동 실패를 막는다.
 */
class CookiePropertiesBindingTest {

  @Test
  void origins_맵이_소문자_role_키로_enum_바인딩된다() {
    Map<String, Object> props =
        Map.of(
            "auth.refresh-cookie.name", "refresh_token",
            "auth.refresh-cookie.path", "/api/v1/auth",
            "auth.refresh-cookie.same-site", "None",
            "auth.refresh-cookie.secure", "true",
            "auth.refresh-cookie.origins.customer", "https://dev.magampick.com",
            "auth.refresh-cookie.origins.seller", "https://owner.dev.magampick.com",
            "auth.refresh-cookie.origins.admin", "https://admin.dev.magampick.com");

    Binder binder =
        new Binder(
            List.of(new MapConfigurationPropertySource(props)),
            null,
            ApplicationConversionService.getSharedInstance());
    CookieProperties bound = binder.bind("auth.refresh-cookie", CookieProperties.class).get();

    assertThat(bound.origins())
        .containsEntry(Role.CUSTOMER, "https://dev.magampick.com")
        .containsEntry(Role.SELLER, "https://owner.dev.magampick.com")
        .containsEntry(Role.ADMIN, "https://admin.dev.magampick.com");
  }

  @Test
  void origins_미설정이면_빈_맵으로_바인딩된다() {
    Map<String, Object> props =
        Map.of(
            "auth.refresh-cookie.name", "refresh_token",
            "auth.refresh-cookie.path", "/api/v1/auth",
            "auth.refresh-cookie.same-site", "Lax",
            "auth.refresh-cookie.secure", "false");

    Binder binder =
        new Binder(
            List.of(new MapConfigurationPropertySource(props)),
            null,
            ApplicationConversionService.getSharedInstance());
    CookieProperties bound = binder.bind("auth.refresh-cookie", CookieProperties.class).get();

    assertThat(bound.origins()).isEmpty();
  }
}
