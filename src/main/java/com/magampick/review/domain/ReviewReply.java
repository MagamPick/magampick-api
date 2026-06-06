package com.magampick.review.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.seller.domain.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사장 답글. 리뷰 1개당 최대 1개 (UNIQUE). write 는 Phase 7 구현 예정. */
@Entity
@Table(name = "review_replies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReply extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "review_id", nullable = false, unique = true)
  private Review review;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private Seller seller;

  @Column(name = "content", nullable = false, length = 500)
  private String content;

  @Builder
  private ReviewReply(Review review, Seller seller, String content) {
    this.review = review;
    this.seller = seller;
    this.content = content;
  }
}
