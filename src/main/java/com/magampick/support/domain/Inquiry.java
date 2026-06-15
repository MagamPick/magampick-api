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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1:1 문의 엔티티. 소비자·사장이 제출하고 관리자가 답변한다. */
@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 작성자 역할 — CUSTOMER 또는 SELLER. */
  @Enumerated(EnumType.STRING)
  @Column(name = "author_role", nullable = false, length = 20)
  private Role authorRole;

  /** 작성자 ID — customers.id 또는 sellers.id (author_role 에 따라 다른 테이블 참조, FK 미설정). */
  @Column(name = "author_id", nullable = false)
  private Long authorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private InquiryCategory category;

  @Column(name = "title", nullable = false, length = 40)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private InquiryStatus status;

  /** 답변 본문. 답변 전에는 null. */
  @Column(name = "answer_content", columnDefinition = "TEXT")
  private String answerContent;

  /** 답변 시각. 답변 전에는 null. */
  @Column(name = "answered_at")
  private LocalDateTime answeredAt;

  @Builder
  private Inquiry(
      Role authorRole, Long authorId, InquiryCategory category, String title, String content) {
    this.authorRole = authorRole;
    this.authorId = authorId;
    this.category = category;
    this.title = title;
    this.content = content;
    this.status = InquiryStatus.PENDING;
  }

  /**
   * 관리자 답변 처리. status = ANSWERED, answerContent / answeredAt 설정.
   *
   * <p>이미 ANSWERED 인 경우 호출 측(SupportService)에서 409 를 던지고 이 메서드를 호출하지 않는다.
   *
   * @param content 답변 본문
   * @param answeredAt 답변 시각
   */
  public void answer(String content, LocalDateTime answeredAt) {
    this.status = InquiryStatus.ANSWERED;
    this.answerContent = content;
    this.answeredAt = answeredAt;
  }
}
