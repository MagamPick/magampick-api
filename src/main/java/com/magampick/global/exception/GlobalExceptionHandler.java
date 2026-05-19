package com.magampick.global.exception;

import com.magampick.global.response.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 전역 예외 처리. 모든 예외를 ApiResponse.error envelope 로 변환한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    BaseErrorCode errorCode = e.getErrorCode();
    log.warn("비즈니스 예외 발생. code={}", errorCode.getCode());
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.error(ErrorResponse.from(errorCode)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    List<ErrorResponse.FieldError> fieldErrors =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorResponse.from(CommonErrorCode.INVALID_INPUT, fieldErrors)));
  }

  @ExceptionHandler({
    MaxUploadSizeExceededException.class,
    MissingServletRequestPartException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleMultipartOrPayload(Exception e) {
    log.warn("요청 본문 파싱 실패. type={}, message={}", e.getClass().getSimpleName(), e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ErrorResponse.from(CommonErrorCode.INVALID_INPUT)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleAll(Exception e) {
    log.error("처리되지 않은 예외", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ErrorResponse.from(CommonErrorCode.INTERNAL_ERROR)));
  }
}
