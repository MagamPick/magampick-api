package com.magampick.terms.domain;

import com.magampick.seller.domain.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 사장-약관 동의 기록. 가입 시점에 동의한 약관(type+version row)을 스냅샷 참조로 남긴다. */
@Entity
@Table(
    name = "seller_terms_agreements",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_seller_terms_agreements_seller_term",
            columnNames = {"seller_id", "term_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerTermsAgreement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private Seller seller;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "term_id", nullable = false)
  private Term term;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private SellerTermsAgreement(Seller seller, Term term) {
    this.seller = seller;
    this.term = term;
  }
}
