package com.magampick.announcement.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 공지사항 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AnnouncementErrorCode implements BaseErrorCode {
  ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND", "공지사항을 찾을 수 없습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
