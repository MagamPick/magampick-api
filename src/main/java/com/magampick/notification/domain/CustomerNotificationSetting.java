package com.magampick.notification.domain;

import com.magampick.global.exception.BusinessException;
import com.magampick.notification.exception.NotificationErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 소비자 알림 수신 설정. 가입 시 기본값으로 생성되며, 항목별 on/off 를 관리한다. */
@Entity
@Table(name = "customer_notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CustomerNotificationSetting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "customer_id", nullable = false, unique = true)
  private Long customerId;

  @Column(name = "nearby_deal", nullable = false)
  private boolean nearbyDeal;

  @Column(name = "favorite_store", nullable = false)
  private boolean favoriteStore;

  @Column(name = "order_refund", nullable = false)
  private boolean orderRefund;

  @Column(name = "review_reply", nullable = false)
  private boolean reviewReply;

  @Column(name = "event_benefit", nullable = false)
  private boolean eventBenefit;

  @Column(name = "marketing", nullable = false)
  private boolean marketing;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  private CustomerNotificationSetting(
      Long customerId,
      boolean nearbyDeal,
      boolean favoriteStore,
      boolean orderRefund,
      boolean reviewReply,
      boolean eventBenefit,
      boolean marketing) {
    this.customerId = customerId;
    this.nearbyDeal = nearbyDeal;
    this.favoriteStore = favoriteStore;
    this.orderRefund = orderRefund;
    this.reviewReply = reviewReply;
    this.eventBenefit = eventBenefit;
    this.marketing = marketing;
    this.updatedAt = LocalDateTime.now();
  }

  /** 기본 알림 설정 생성. 가입 시 호출. */
  public static CustomerNotificationSetting defaultFor(Long customerId) {
    return CustomerNotificationSetting.builder()
        .customerId(customerId)
        .nearbyDeal(true)
        .favoriteStore(true)
        .orderRefund(true)
        .reviewReply(true)
        .eventBenefit(false)
        .marketing(false)
        .build();
  }

  /**
   * 키 이름으로 알림 설정 변경.
   *
   * @param key camelCase 필드명 (nearbyDeal, favoriteStore, orderRefund, reviewReply, eventBenefit,
   *     marketing)
   * @param enabled 활성화 여부
   */
  public void updateKey(String key, boolean enabled) {
    switch (key) {
      case "nearbyDeal" -> this.nearbyDeal = enabled;
      case "favoriteStore" -> this.favoriteStore = enabled;
      case "orderRefund" -> this.orderRefund = enabled;
      case "reviewReply" -> this.reviewReply = enabled;
      case "eventBenefit" -> this.eventBenefit = enabled;
      case "marketing" -> this.marketing = enabled;
      default ->
          throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_SETTING_KEY);
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
