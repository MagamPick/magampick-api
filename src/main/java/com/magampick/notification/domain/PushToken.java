package com.magampick.notification.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.global.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 푸시 디바이스 토큰. 소유자는 polymorphic — {@code (ownerType, ownerId)} 으로 소비자/사장을 가리킨다(공유 users 테이블 없음 →
 * FK 미사용). 같은 토큰은 한 행만(UNIQUE) — 기기 공유/재로그인 시 소유자를 재할당한다.
 */
@Entity
@Table(
    name = "push_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uk_push_tokens_token", columnNames = "token"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 토큰 소유자 종류 — CUSTOMER 또는 SELLER (admin 웹은 FCM 미사용). */
  @Enumerated(EnumType.STRING)
  @Column(name = "owner_type", nullable = false, length = 20)
  private Role ownerType;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(name = "token", nullable = false, length = 512)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform", nullable = false, length = 20)
  private Platform platform;

  @Builder
  private PushToken(Role ownerType, Long ownerId, String token, Platform platform) {
    this.ownerType = ownerType;
    this.ownerId = ownerId;
    this.token = token;
    this.platform = platform;
  }

  /** 같은 토큰이 다른 사용자로 재등록될 때 소유자 재할당 (기기 공유·재로그인 대응). */
  public void reassignTo(Role ownerType, Long ownerId) {
    this.ownerType = ownerType;
    this.ownerId = ownerId;
  }

  /** FCM 푸시 대상 플랫폼. 현재 PWA(WEB)만. */
  public enum Platform {
    WEB
  }
}
