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

/** 사장 알림 수신 설정. 가입 시 기본값으로 생성되며, 항목별 on/off 를 관리한다. */
@Entity
@Table(name = "seller_notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SellerNotificationSetting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "seller_id", nullable = false, unique = true)
  private Long sellerId;

  @Column(name = "new_order", nullable = false)
  private boolean newOrder;

  @Column(name = "order_cancel", nullable = false)
  private boolean orderCancel;

  @Column(name = "refund_request", nullable = false)
  private boolean refundRequest;

  @Column(name = "new_review", nullable = false)
  private boolean newReview;

  @Column(name = "notice", nullable = false)
  private boolean notice;

  @Column(name = "marketing", nullable = false)
  private boolean marketing;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  private SellerNotificationSetting(
      Long sellerId,
      boolean newOrder,
      boolean orderCancel,
      boolean refundRequest,
      boolean newReview,
      boolean notice,
      boolean marketing) {
    this.sellerId = sellerId;
    this.newOrder = newOrder;
    this.orderCancel = orderCancel;
    this.refundRequest = refundRequest;
    this.newReview = newReview;
    this.notice = notice;
    this.marketing = marketing;
    this.updatedAt = LocalDateTime.now();
  }

  /** 기본 알림 설정 생성. 가입 시 호출. */
  public static SellerNotificationSetting defaultFor(Long sellerId) {
    return SellerNotificationSetting.builder()
        .sellerId(sellerId)
        .newOrder(true)
        .orderCancel(true)
        .refundRequest(true)
        .newReview(true)
        .notice(true)
        .marketing(false)
        .build();
  }

  /**
   * 키 이름으로 알림 설정 변경.
   *
   * @param key camelCase 필드명 (newOrder, orderCancel, refundRequest, newReview, notice, marketing)
   * @param enabled 활성화 여부
   */
  public void updateKey(String key, boolean enabled) {
    switch (key) {
      case "newOrder" -> this.newOrder = enabled;
      case "orderCancel" -> this.orderCancel = enabled;
      case "refundRequest" -> this.refundRequest = enabled;
      case "newReview" -> this.newReview = enabled;
      case "notice" -> this.notice = enabled;
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
