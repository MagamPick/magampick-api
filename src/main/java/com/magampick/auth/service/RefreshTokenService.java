package com.magampick.auth.service;

import com.magampick.auth.dto.IssuedTokens;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.repository.RefreshTokenStore;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.exception.AuthErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** access/refresh 발급 + refresh 세션(Redis) 검증·무효화. rotation 은 백로그 (갱신 시 새 access 만 발급). */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenStore store;

  /** access + refresh 발급. refresh 는 Redis 에 세션으로 기록한다. */
  public IssuedTokens issueTokens(Long userId, Role role) {
    // 토큰 생성
    String accessToken = jwtProvider.issueAccessToken(userId, role);
    String refreshToken = jwtProvider.issueRefreshToken(userId, role);
    JwtProvider.TokenPayload payload = jwtProvider.parsePayload(refreshToken);
    // 세션 저장
    store.save(
        role,
        userId,
        payload.tokenId(),
        hashToken(refreshToken),
        Duration.ofSeconds(jwtProvider.refreshTokenExpiresInSeconds()));
    return new IssuedTokens(accessToken, refreshToken, jwtProvider.accessTokenExpiresInSeconds());
  }

  /** 쿠키 refresh 로 새 access 만 발급 (rotation X). 서명·Redis 세션 검증 실패 시 REFRESH_INVALID. */
  public TokenResponse reissueAccess(String rawRefreshToken) {
    JwtProvider.TokenPayload payload = validate(rawRefreshToken);
    String accessToken = jwtProvider.issueAccessToken(payload.userId(), payload.role());
    return new TokenResponse(accessToken, jwtProvider.accessTokenExpiresInSeconds());
  }

  /** 로그아웃 — refresh 세션 키 삭제. 이미 만료/무효한 토큰은 멱등 무시. */
  public void revoke(String rawRefreshToken) {
    try {
      JwtProvider.TokenPayload payload = jwtProvider.parsePayload(rawRefreshToken);
      store.delete(payload.role(), payload.userId(), payload.tokenId());
    } catch (BusinessException ignored) {
      // 만료/무효 토큰의 로그아웃 — 삭제할 키 없음, 무시
    }
  }

  /** 비밀번호 재설정 후 해당 사용자의 모든 refresh 세션을 폐기한다. */
  public void revokeAll(Role role, Long userId) {
    store.deleteAll(role, userId);
  }

  /** 비밀번호 변경 후 현재 refresh 세션만 유지하고 같은 사용자의 다른 세션을 폐기한다. */
  public void revokeOtherSessions(String rawRefreshToken) {
    JwtProvider.TokenPayload payload = validate(rawRefreshToken);
    store.deleteAllExcept(payload.role(), payload.userId(), payload.tokenId());
  }

  private JwtProvider.TokenPayload validate(String rawRefreshToken) {
    // JWT 파싱
    JwtProvider.TokenPayload payload;
    try {
      payload = jwtProvider.parsePayload(rawRefreshToken);
    } catch (BusinessException e) {
      throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
    }
    // Redis 세션 검증
    if (!store.isValid(
        payload.role(), payload.userId(), payload.tokenId(), hashToken(rawRefreshToken))) {
      throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
    }
    return payload;
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
    }
  }
}
