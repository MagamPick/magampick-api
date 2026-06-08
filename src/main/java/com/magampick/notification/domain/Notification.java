package com.magampick.notification.domain;

import com.magampick.global.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 수신함 알림 레코드. 발송된 알림을 수신자별로 보관 — 읽음 처리 / 조회에 사용한다. {@code (receiverType, receiverId)} 로 소비자/사장을
 * polymorphic 참조.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 수신자 종류 — CUSTOMER 또는 SELLER. */
  @Enumerated(EnumType.STRING)
  @Column(name = "receiver_type", nullable = false, length = 10)
  private Role receiverType;

  @Column(name = "receiver_id", nullable = false)
  private Long receiverId;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private NotificationCategory category;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "link", length = 500)
  private String link;

  @Column(name = "is_read", nullable = false)
  private boolean isRead;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @Builder
  private Notification(
      Role receiverType,
      Long receiverId,
      NotificationCategory category,
      String title,
      String body,
      String link) {
    this.receiverType = receiverType;
    this.receiverId = receiverId;
    this.category = category;
    this.title = title;
    this.body = body;
    this.link = link;
    this.isRead = false;
  }

  /** 새 알림 생성. */
  public static Notification create(
      Role receiverType,
      Long receiverId,
      NotificationCategory category,
      String title,
      String body,
      String link) {
    return Notification.builder()
        .receiverType(receiverType)
        .receiverId(receiverId)
        .category(category)
        .title(title)
        .body(body)
        .link(link)
        .build();
  }

  /** 알림 읽음 처리. */
  public void markAsRead() {
    this.isRead = true;
  }
}
