package com.magampick.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 리뷰 사진. sort_order 오름차순 정렬로 표시. */
@Entity
@Table(name = "review_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "review_id", nullable = false)
  private Review review;

  @Column(name = "url", nullable = false, length = 500)
  private String url;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Builder
  private ReviewImage(Review review, String url, int sortOrder) {
    this.review = review;
    this.url = url;
    this.sortOrder = sortOrder;
  }
}
