package com.magampick.auth.repository;

import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.Role;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** 비밀번호 재설정 본인확인 완료 토큰 저장소. TTL 15분, 1회용. */
@Repository
@RequiredArgsConstructor
public class PasswordResetStore {

  private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
  private static final String FIELD_ROLE = "role";
  private static final String FIELD_USER_ID = "userId";

  private final StringRedisTemplate redis;

  public String issueToken(Role role, Long userId) {
    String token = UUID.randomUUID().toString();
    String key = key(token);
    redis.opsForHash().put(key, FIELD_ROLE, role.name());
    redis.opsForHash().put(key, FIELD_USER_ID, String.valueOf(userId));
    redis.expire(key, TOKEN_TTL);
    return token;
  }

  public Subject consume(String token) {
    Subject subject =
        find(token)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.RESET_VERIFICATION_FAILED));
    redis.delete(key(token));
    return subject;
  }

  private Optional<Subject> find(String token) {
    Map<Object, Object> entries = redis.opsForHash().entries(key(token));
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Subject(
            Role.valueOf((String) entries.get(FIELD_ROLE)),
            Long.valueOf((String) entries.get(FIELD_USER_ID))));
  }

  private String key(String token) {
    return "password-reset:token:" + token;
  }

  public record Subject(Role role, Long userId) {}
}
