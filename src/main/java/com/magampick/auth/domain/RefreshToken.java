package com.magampick.auth.domain;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "owner_role", nullable = false, length = 20)
  private Role ownerRole;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Builder
  private RefreshToken(
      Long ownerId,
      Role ownerRole,
      String tokenHash,
      LocalDateTime expiresAt,
      LocalDateTime revokedAt) {
    this.ownerId = ownerId;
    this.ownerRole = ownerRole;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(LocalDateTime now) {
    return expiresAt.isBefore(now) || expiresAt.isEqual(now);
  }

  public void revoke(LocalDateTime now) {
    this.revokedAt = now;
  }
}
