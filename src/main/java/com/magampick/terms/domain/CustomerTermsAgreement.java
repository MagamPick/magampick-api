package com.magampick.terms.domain;

import com.magampick.customer.domain.Customer;
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

/**
 * 소비자-약관 동의 기록. 가입 시점에 동의한 약관(특정 type+version row)을 term 참조로 기록한다. 동의 시각 = {@code createdAt} (row
 * 생성 시점). 불변 — 한 번 동의하면 수정하지 않는다.
 */
@Entity
@Table(
    name = "customer_terms_agreements",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_customer_terms_agreements_customer_term",
            columnNames = {"customer_id", "term_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerTermsAgreement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "term_id", nullable = false)
  private Term term;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private CustomerTermsAgreement(Customer customer, Term term) {
    this.customer = customer;
    this.term = term;
  }
}
