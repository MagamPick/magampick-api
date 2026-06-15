package com.magampick.auth.repository;

import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.auth.oauth.OAuthUserInfo;
import com.magampick.global.exception.BusinessException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 신규 카카오 회원의 추가정보 가입 대기용 소셜 토큰 저장소(Redis). 1단계(/kakao) 분기에서 카카오 프로필을 보관하고 2단계(/signup/social)에서
 * 소비한다. TTL 15분·1회용 (본인인증 토큰과 동일 패턴 — {@code PhoneVerificationStore}).
 */
@Repository
@RequiredArgsConstructor
public class SocialAuthStore {

  private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
  private static final String FIELD_PROVIDER_USER_ID = "providerUserId";
  private static final String FIELD_EMAIL = "email";
  private static final String FIELD_NICKNAME = "nickname";

  private final StringRedisTemplate redis;

  /** 카카오 프로필을 보관하고 소셜 토큰을 발급한다. */
  public String issueToken(OAuthUserInfo userInfo) {
    String token = UUID.randomUUID().toString();
    String key = key(token);
    redis.opsForHash().put(key, FIELD_PROVIDER_USER_ID, userInfo.providerUserId());
    redis.opsForHash().put(key, FIELD_EMAIL, userInfo.email());
    if (userInfo.nickname() != null) {
      redis.opsForHash().put(key, FIELD_NICKNAME, userInfo.nickname());
    }
    redis.expire(key, TOKEN_TTL);
    return token;
  }

  /** 소셜 토큰의 카카오 프로필을 조회한다 (소비하지 않음). 없으면 SOCIAL_TOKEN_INVALID. */
  public OAuthUserInfo require(String token) {
    return find(token).orElseThrow(() -> new BusinessException(AuthErrorCode.SOCIAL_TOKEN_INVALID));
  }

  /** 가입 완료 시 소셜 토큰을 소비(삭제)한다. */
  public void delete(String token) {
    redis.delete(key(token));
  }

  private Optional<OAuthUserInfo> find(String token) {
    Map<Object, Object> entries = redis.opsForHash().entries(key(token));
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new OAuthUserInfo(
            (String) entries.get(FIELD_PROVIDER_USER_ID),
            (String) entries.get(FIELD_EMAIL),
            (String) entries.get(FIELD_NICKNAME)));
  }

  private String key(String token) {
    return "social:token:" + token;
  }
}
