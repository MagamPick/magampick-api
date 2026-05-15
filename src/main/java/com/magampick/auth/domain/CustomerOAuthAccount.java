package com.magampick.auth.domain;

import com.magampick.customer.domain.Customer;
import com.magampick.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_oauth_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOAuthAccount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false, unique = true)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 20)
  private OAuthProviderType provider;

  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @Builder
  private CustomerOAuthAccount(
      Customer customer, OAuthProviderType provider, String providerUserId) {
    this.customer = customer;
    this.provider = provider;
    this.providerUserId = providerUserId;
  }
}
