package com.magampick.auth.service;

import com.magampick.auth.domain.RefreshToken;
import com.magampick.auth.dto.TokenResponse;
import com.magampick.auth.repository.RefreshTokenRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.exception.AuthErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenRepository refreshTokenRepository;

  @Transactional
  public TokenResponse issueTokens(Long userId, Role role) {
    String accessToken = jwtProvider.issueAccessToken(userId, role);
    String refreshToken = jwtProvider.issueRefreshToken(userId, role);

    refreshTokenRepository.save(
        RefreshToken.builder()
            .ownerId(userId)
            .ownerRole(role)
            .tokenHash(hashToken(refreshToken))
            .expiresAt(jwtProvider.parsePayload(refreshToken).expiresAt())
            .build());

    return new TokenResponse(accessToken, refreshToken, jwtProvider.accessTokenExpiresInSeconds());
  }

  public String hashToken(String token) {
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

  @Transactional(readOnly = true)
  public RefreshToken getActiveByRawToken(String rawToken) {
    return refreshTokenRepository
        .findByTokenHashAndRevokedAtIsNull(hashToken(rawToken))
        .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));
  }

  @Transactional
  public void revoke(RefreshToken refreshToken) {
    refreshToken.revoke(LocalDateTime.now());
  }
}
