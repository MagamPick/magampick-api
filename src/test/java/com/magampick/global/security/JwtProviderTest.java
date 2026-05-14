package com.magampick.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.exception.AuthErrorCode;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String SECRET = "magampick-test-secret-key-which-is-long-enough-256bit";

  private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(SECRET, 30, 14));

  @Test
  void 액세스_토큰_발급_후_파싱_성공() {
    // given
    String token = jwtProvider.issueAccessToken(42L, Role.CUSTOMER);

    // when
    CustomUserDetails userDetails = jwtProvider.parse(token);

    // then
    assertThat(userDetails.getUserId()).isEqualTo(42L);
    assertThat(userDetails.getRole()).isEqualTo(Role.CUSTOMER);
  }

  @Test
  void 토큰_파싱_userId_role_claim_일치() {
    // given
    String token = jwtProvider.issueAccessToken(7L, Role.SELLER);

    // when
    CustomUserDetails userDetails = jwtProvider.parse(token);

    // then
    assertThat(userDetails.getUsername()).isEqualTo("7");
    assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_SELLER");
  }

  @Test
  void 만료된_토큰_파싱_시_예외() {
    // given - 음수 만료로 이미 만료된 토큰 발급
    JwtProvider expiredProvider = new JwtProvider(new JwtProperties(SECRET, -1, 14));
    String expiredToken = expiredProvider.issueAccessToken(1L, Role.CUSTOMER);

    // when / then
    assertThatThrownBy(() -> jwtProvider.parse(expiredToken))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TOKEN_EXPIRED);
  }

  @Test
  void 잘못된_서명_토큰_파싱_시_예외() {
    // given - 다른 secret 으로 서명한 토큰
    JwtProvider otherProvider =
        new JwtProvider(
            new JwtProperties("another-different-secret-key-also-long-enough-256bit", 30, 14));
    String foreignToken = otherProvider.issueAccessToken(1L, Role.CUSTOMER);

    // when / then
    assertThatThrownBy(() -> jwtProvider.parse(foreignToken))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);
  }
}
