package com.magampick.seller.domain;

import com.magampick.global.common.BaseEntity;
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
@Table(name = "sellers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, length = 255, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 60)
  private String passwordHash;

  @Column(name = "owner_name", nullable = false, length = 20)
  private String ownerName;

  @Column(name = "business_number", nullable = false, length = 10)
  private String businessNumber;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "phone_verified_at")
  private LocalDateTime phoneVerifiedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 20)
  private SellerVerificationStatus verificationStatus;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private Seller(
      String email,
      String passwordHash,
      String ownerName,
      String businessNumber,
      String phone,
      LocalDateTime phoneVerifiedAt,
      SellerVerificationStatus verificationStatus) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.ownerName = ownerName;
    this.businessNumber = businessNumber;
    this.phone = phone;
    this.phoneVerifiedAt = phoneVerifiedAt;
    this.verificationStatus = verificationStatus;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public boolean isApproved() {
    return verificationStatus == SellerVerificationStatus.APPROVED;
  }

  public void changeOwnerName(String newOwnerName) {
    this.ownerName = newOwnerName;
  }

  public void changePhone(String newPhone, LocalDateTime verifiedAt) {
    this.phone = newPhone;
    this.phoneVerifiedAt = verifiedAt;
  }
}
