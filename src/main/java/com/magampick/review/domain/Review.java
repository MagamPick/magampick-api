package com.magampick.review.domain;

import com.magampick.customer.domain.Customer;
import com.magampick.global.common.BaseEntity;
import com.magampick.order.domain.Order;
import com.magampick.store.domain.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 리뷰. 주문(order) 단위 1:1. */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "store_id", nullable = false)
  private Store store;

  @Column(name = "rating", nullable = false)
  private int rating;

  @Column(name = "content", length = 300)
  private String content;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  private List<ReviewImage> reviewImages = new ArrayList<>();

  @OneToOne(mappedBy = "review", fetch = FetchType.LAZY)
  private ReviewReply reviewReply;

  @ElementCollection
  @CollectionTable(name = "review_tags", joinColumns = @JoinColumn(name = "review_id"))
  @Column(name = "tag", length = 20)
  @Enumerated(EnumType.STRING)
  private Set<ReviewTag> tags = new HashSet<>();

  @Builder
  private Review(Customer customer, Order order, Store store, int rating, String content) {
    this.customer = customer;
    this.order = order;
    this.store = store;
    this.rating = rating;
    this.content = content;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** 소비자 소유권 판단. */
  public boolean isOwnedBy(Long customerId) {
    return customer.getId().equals(customerId);
  }

  /** soft-delete. */
  public void delete() {
    this.deletedAt = LocalDateTime.now();
  }

  /** 리뷰 내용 수정. */
  public void update(int rating, String content, Set<ReviewTag> newTags) {
    this.rating = rating;
    this.content = content;
    this.tags.clear();
    if (newTags != null) this.tags.addAll(newTags);
  }

  /** 이미지 전체 삭제 (수정 시 재업로드 전 호출). */
  public void clearImages() {
    this.reviewImages.clear();
  }

  /** 이미지 추가. */
  public void addImage(ReviewImage image) {
    this.reviewImages.add(image);
  }

  /** 사장 답글 존재 여부. */
  public boolean hasReply() {
    return this.reviewReply != null;
  }
}
