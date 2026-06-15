package com.magampick.auth.repository;

import com.magampick.global.security.Role;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * refresh 토큰 세션 저장소 (Redis). 키 {@code refresh:{role}:{id}:{tokenId}}, value = refresh JWT 의 hash.
 * TTL 자동 만료 + 로그아웃 시 키 삭제로 무효화한다 (로그인/로그아웃 명세 — stateless JWT 와 분리된 세션 추적, AOF 영속).
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenStore {

  private final StringRedisTemplate redis;

  public void save(Role role, Long userId, String tokenId, String tokenHash, Duration ttl) {
    redis.opsForValue().set(key(role, userId, tokenId), tokenHash, ttl);
  }

  /** 키가 존재(미무효화)하고 저장된 hash 가 제시된 hash 와 일치하는지. */
  public boolean isValid(Role role, Long userId, String tokenId, String tokenHash) {
    String stored = redis.opsForValue().get(key(role, userId, tokenId));
    return stored != null && stored.equals(tokenHash);
  }

  public void delete(Role role, Long userId, String tokenId) {
    redis.delete(key(role, userId, tokenId));
  }

  public void deleteAll(Role role, Long userId) {
    Set<String> keys = redis.keys(prefix(role, userId) + "*");
    if (keys != null && !keys.isEmpty()) {
      redis.delete(keys);
    }
  }

  public void deleteAllExcept(Role role, Long userId, String exceptTokenId) {
    Set<String> keys = redis.keys(prefix(role, userId) + "*");
    if (keys == null || keys.isEmpty()) {
      return;
    }
    Set<String> targets = new HashSet<>(keys);
    targets.remove(key(role, userId, exceptTokenId));
    if (!targets.isEmpty()) {
      redis.delete(targets);
    }
  }

  private String key(Role role, Long userId, String tokenId) {
    return prefix(role, userId) + tokenId;
  }

  private String prefix(Role role, Long userId) {
    return "refresh:" + role.name().toLowerCase() + ":" + userId + ":";
  }
}
