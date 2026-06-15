package com.magampick.global.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 컨트롤러 반환값을 ApiResponse.success 로 자동 wrap 한다. com.magampick 컨트롤러에만 적용 (springdoc / actuator 응답 제외).
 */
@RestControllerAdvice(basePackages = "com.magampick")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    // body 없음 (204 No Content 등) → envelope 없이 통과
    if (body == null) {
      return null;
    }
    // 이미 envelope (에러 응답 등) → 이중 wrap 방지
    if (body instanceof ApiResponse<?>) {
      return body;
    }
    return ApiResponse.success(body);
  }
}
