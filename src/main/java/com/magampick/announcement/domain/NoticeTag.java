package com.magampick.announcement.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 공지사항 태그. Jackson 직렬화: lowercase ("notice" / "event" / "update"). */
public enum NoticeTag {
  NOTICE("notice"),
  EVENT("event"),
  UPDATE("update");

  private final String value;

  NoticeTag(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * 소문자 문자열에서 NoticeTag 역직렬화. 잘못된 값이면 IllegalArgumentException.
   *
   * @param value 소문자 태그 값 ("notice" / "event" / "update")
   * @return 매핑된 NoticeTag
   */
  @JsonCreator
  public static NoticeTag from(String value) {
    for (NoticeTag tag : values()) {
      if (tag.value.equalsIgnoreCase(value)) {
        return tag;
      }
    }
    throw new IllegalArgumentException("알 수 없는 NoticeTag 값: " + value);
  }
}
