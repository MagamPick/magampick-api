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
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),
  INVALID_NOTIFICATION_SETTING_KEY(
      HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION_SETTING_KEY", "유효하지 않은 알림 설정 키입니다."),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
