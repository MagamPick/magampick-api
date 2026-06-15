package com.magampick.announcement.domain;

import com.magampick.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공지사항 엔티티. NOTICE / EVENT / UPDATE 태그를 가진 단일 글로벌 공지 목록. */
@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "tag", nullable = false, length = 20)
  private NoticeTag tag;

  @Column(name = "pinned", nullable = false)
  private boolean pinned;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  /** 발행일. FE 계약의 date 필드에 해당. */
  @Column(name = "published_at", nullable = false)
  private LocalDate publishedAt;

  @Builder
  private Announcement(
      NoticeTag tag, boolean pinned, String title, String body, LocalDate publishedAt) {
    this.tag = tag;
    this.pinned = pinned;
    this.title = title;
    this.body = body;
    this.publishedAt = publishedAt;
  }

  /**
   * 부분 수정 — null 인 인자는 무시한다.
   *
   * @param tag 새 태그 (null 이면 현재 값 유지)
   * @param pinned 새 핀 여부 (null 이면 현재 값 유지)
   * @param title 새 제목 (null 이면 현재 값 유지)
   * @param body 새 본문 (null 이면 현재 값 유지)
   */
  public void update(NoticeTag tag, Boolean pinned, String title, String body) {
    if (tag != null) this.tag = tag;
    if (pinned != null) this.pinned = pinned;
    if (title != null) this.title = title;
    if (body != null) this.body = body;
  }
}
