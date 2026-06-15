package com.magampick.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.magampick.auth.dto.IssuedTokens;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.repository.RefreshTokenStore;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.exception.AuthErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock JwtProvider jwtProvider;
  @Mock RefreshTokenStore store;

  @InjectMocks RefreshTokenService refreshTokenService;

  private JwtProvider.TokenPayload payload(String tokenId) {
    return new JwtProvider.TokenPayload(
        1L, Role.CUSTOMER, tokenId, LocalDateTime.now().plusDays(30));
  }

  @Test
  void 토큰_발급_시_refresh_를_Redis_세션으로_저장() {
    // given
    given(jwtProvider.issueAccessToken(1L, Role.CUSTOMER)).willReturn("access");
    given(jwtProvider.issueRefreshToken(1L, Role.CUSTOMER)).willReturn("refresh");
    given(jwtProvider.parsePayload("refresh")).willReturn(payload("jti1"));
    given(jwtProvider.refreshTokenExpiresInSeconds()).willReturn(2_592_000L);
    given(jwtProvider.accessTokenExpiresInSeconds()).willReturn(1800L);

    // when
    IssuedTokens tokens = refreshTokenService.issueTokens(1L, Role.CUSTOMER);

    // then
    assertThat(tokens.accessToken()).isEqualTo("access");
    assertThat(tokens.refreshToken()).isEqualTo("refresh");
    verify(store).save(eq(Role.CUSTOMER), eq(1L), eq("jti1"), anyString(), any(Duration.class));
  }

  @Test
  void 유효한_refresh_로_새_access_재발급_rotation_없음() {
    // given
    given(jwtProvider.parsePayload("rawR")).willReturn(payload("jti1"));
    given(store.isValid(eq(Role.CUSTOMER), eq(1L), eq("jti1"), anyString())).willReturn(true);
    given(jwtProvider.issueAccessToken(1L, Role.CUSTOMER)).willReturn("newAccess");
    given(jwtProvider.accessTokenExpiresInSeconds()).willReturn(1800L);

    // when
    TokenResponse response = refreshTokenService.reissueAccess("rawR");

    // then — refresh 재발급 안 함(새 refresh issue 호출 없음)
    assertThat(response.accessToken()).isEqualTo("newAccess");
  }

  @Test
  void 만료_무효_refresh_갱신_시_REFRESH_INVALID() {
    // given — 서명/만료 검증 실패
    given(jwtProvider.parsePayload("badR"))
        .willThrow(new BusinessException(AuthErrorCode.TOKEN_EXPIRED));

    // when & then
    assertThatThrownBy(() -> refreshTokenService.reissueAccess("badR"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AuthErrorCode.REFRESH_INVALID);
  }

  @Test
  void Redis_세션_없으면_REFRESH_INVALID() {
    // given — 서명은 통과하나 Redis 세션 없음(무효화/타기기)
    given(jwtProvider.parsePayload("rawR")).willReturn(payload("jti1"));
    given(store.isValid(eq(Role.CUSTOMER), eq(1L), eq("jti1"), anyString())).willReturn(false);

    // when & then
    assertThatThrownBy(() -> refreshTokenService.reissueAccess("rawR"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AuthErrorCode.REFRESH_INVALID);
  }

  @Test
  void 로그아웃_시_refresh_세션_삭제() {
    // given
    given(jwtProvider.parsePayload("rawR")).willReturn(payload("jti1"));

    // when
    refreshTokenService.revoke("rawR");

    // then
    verify(store).delete(Role.CUSTOMER, 1L, "jti1");
  }
}
