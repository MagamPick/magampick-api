package com.magampick.terms.domain;

import com.magampick.global.common.BaseEntity;
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

/** 약관 마스터. (type, version) 별 1 row. 가입 화면이 조회해 표시한다. */
@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Term extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30)
  private TermType type;

  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "required", nullable = false)
  private boolean required;

  @Builder
  private Term(TermType type, int version, String title, String body, boolean required) {
    this.type = type;
    this.version = version;
    this.title = title;
    this.body = body;
    this.required = required;
  }
}
