package com.magampick.customer.domain;

import com.magampick.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, length = 255, unique = true)
  private String email;

  @Column(name = "password_hash", length = 60)
  private String passwordHash;

  @Column(name = "nickname", nullable = false, length = 20)
  private String nickname;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "phone_verified_at")
  private LocalDateTime phoneVerifiedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private Customer(
      String email,
      String passwordHash,
      String nickname,
      String phone,
      LocalDateTime phoneVerifiedAt) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.nickname = nickname;
    this.phone = phone;
    this.phoneVerifiedAt = phoneVerifiedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void changeNickname(String newNickname) {
    this.nickname = newNickname;
  }

  public void changePhone(String newPhone, LocalDateTime verifiedAt) {
    this.phone = newPhone;
    this.phoneVerifiedAt = verifiedAt;
  }
}
