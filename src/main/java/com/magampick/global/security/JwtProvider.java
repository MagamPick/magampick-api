package com.magampick.global.security;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.exception.AuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT 발급 / 검증 유틸. HS256, claim 은 sub(userId) + role (auth.md §4). */
@Component
public class JwtProvider {

  private static final String ISSUER = "magampick-api";
  private static final String ROLE_CLAIM = "role";

  private final SecretKey key;
  private final Duration accessTokenValidity;
  private final Duration refreshTokenValidity;

  public JwtProvider(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.accessTokenValidity = Duration.ofMinutes(properties.accessTokenValidityMinutes());
    this.refreshTokenValidity = Duration.ofDays(properties.refreshTokenValidityDays());
  }

  public String issueAccessToken(Long userId, Role role) {
    return issueToken(userId, role, accessTokenValidity);
  }

  public String issueRefreshToken(Long userId, Role role) {
    return issueToken(userId, role, refreshTokenValidity);
  }

  /** 토큰을 검증하고 claim 으로 인증 주체를 만든다. 실패 시 BusinessException(AuthErrorCode). */
  public CustomUserDetails parse(String token) {
    TokenPayload payload = parsePayload(token);
    return new CustomUserDetails(payload.userId(), payload.role());
  }

  /** 토큰을 검증하고 payload 를 반환한다. refresh token 처리에서 사용자 일치 검증에 사용한다. */
  public TokenPayload parsePayload(String token) {
    Claims claims = parseClaims(token);
    Long userId = Long.valueOf(claims.getSubject());
    Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    LocalDateTime expiresAt =
        LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault());
    return new TokenPayload(userId, role, expiresAt);
  }

  public long accessTokenExpiresInSeconds() {
    return accessTokenValidity.toSeconds();
  }

  private String issueToken(Long userId, Role role, Duration validity) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(String.valueOf(userId))
        .claim(ROLE_CLAIM, role.name())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(validity)))
        .signWith(key)
        .compact();
  }

  private Claims parseClaims(String token) {
    try {
      return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      throw new BusinessException(AuthErrorCode.TOKEN_EXPIRED);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
    }
  }

  public record TokenPayload(Long userId, Role role, LocalDateTime expiresAt) {}
}
