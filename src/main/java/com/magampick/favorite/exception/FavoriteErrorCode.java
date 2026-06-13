package com.magampick.favorite.exception;

import com.magampick.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 단골(즐겨찾기) 도메인 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum FavoriteErrorCode implements BaseErrorCode {
  FAVORITE_LIMIT_REACHED(HttpStatus.CONFLICT, "FAVORITE_LIMIT_REACHED", "단골 매장은 최대 50개까지 등록 가능합니다"),
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}
