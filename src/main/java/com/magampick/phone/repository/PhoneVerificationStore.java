package com.magampick.phone.repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 본인인증 단기 데이터(OTP·발송 제한·본인인증 토큰)의 Redis 저장소. TTL 자동 만료 + 1회용을 Redis 로 강제한다 (JWT stateless 메인 인증과 분리
 * — 노션 본인인증 명세).
 */
@Repository
@RequiredArgsConstructor
public class PhoneVerificationStore {

  private static final Duration OTP_TTL = Duration.ofMinutes(3);
  private static final Duration COOLDOWN_TTL = Duration.ofSeconds(30);
  private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
  private static final Duration DAILY_TTL = Duration.ofDays(1);

  private static final String FIELD_CODE = "code";
  private static final String FIELD_ATTEMPTS = "attempts";

  private final StringRedisTemplate redis;

  // --- 발송 제한: 재발송 쿨다운 (SET NX — 동시 중복 발송도 1건만 통과) ---
  public boolean tryAcquireResendCooldown(String phone) {
    Boolean acquired = redis.opsForValue().setIfAbsent(cooldownKey(phone), "1", COOLDOWN_TTL);
    return Boolean.TRUE.equals(acquired);
  }

  public void releaseResendCooldown(String phone) {
    redis.delete(cooldownKey(phone));
  }

  // --- 발송 제한: 일일 발송 카운트 ---
  public long currentDailyCount(String phone, LocalDate date) {
    String value = redis.opsForValue().get(dailyKey(phone, date));
    return value == null ? 0L : Long.parseLong(value);
  }

  public void incrementDailyCount(String phone, LocalDate date) {
    String key = dailyKey(phone, date);
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, DAILY_TTL);
    }
  }

  // --- OTP (code + 시도 횟수, 3분 TTL) ---
  public void saveOtp(String phone, String code) {
    String key = otpKey(phone);
    redis.delete(key);
    redis.opsForHash().put(key, FIELD_CODE, code);
    redis.expire(key, OTP_TTL);
  }

  public Optional<String> findOtpCode(String phone) {
    Object code = redis.opsForHash().get(otpKey(phone), FIELD_CODE);
    return code == null ? Optional.empty() : Optional.of(code.toString());
  }

  /** 검증 시도 횟수 +1 (HINCRBY — 없으면 0 에서 시작). OTP 키의 TTL 은 유지된다. */
  public long incrementOtpAttempts(String phone) {
    Long attempts = redis.opsForHash().increment(otpKey(phone), FIELD_ATTEMPTS, 1L);
    return attempts == null ? 0L : attempts;
  }

  public void deleteOtp(String phone) {
    redis.delete(otpKey(phone));
  }

  // --- 본인인증 토큰 (opaque, 15분 TTL, 1회용) ---
  public String issueToken(String phone) {
    String token = UUID.randomUUID().toString();
    redis.opsForValue().set(tokenKey(token), phone, TOKEN_TTL);
    return token;
  }

  public Optional<String> findTokenPhone(String token) {
    return Optional.ofNullable(redis.opsForValue().get(tokenKey(token)));
  }

  public void deleteToken(String token) {
    redis.delete(tokenKey(token));
  }

  private String otpKey(String phone) {
    return "pv:otp:" + phone;
  }

  private String cooldownKey(String phone) {
    return "pv:cooldown:" + phone;
  }

  private String dailyKey(String phone, LocalDate date) {
    return "pv:daily:" + phone + ":" + date;
  }

  private String tokenKey(String token) {
    return "pv:token:" + token;
  }
}
