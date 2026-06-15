package com.magampick.support.domain;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FAQ 엔티티. audience (CUSTOMER / SELLER) 별로 sort_order 순 표시. */
@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 대상 역할 — CUSTOMER 또는 SELLER. */
  @Enumerated(EnumType.STRING)
  @Column(name = "audience", nullable = false, length = 20)
  private Role audience;

  @Column(name = "question", nullable = false, length = 200)
  private String question;

  @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
  private String answer;

  /** 표시 순서. 0 부터 시작. */
  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private Faq(Role audience, String question, String answer, int sortOrder) {
    this.audience = audience;
    this.question = question;
    this.answer = answer;
    this.sortOrder = sortOrder;
  }
}
