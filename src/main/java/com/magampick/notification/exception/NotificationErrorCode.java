package com.magampick.notification.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 알림(FCM) 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {
  FCM_SEND_FAILED(HttpStatus.BAD_GATEWAY, "FCM_SEND_FAILED", "푸시 발송에 실패했습니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
