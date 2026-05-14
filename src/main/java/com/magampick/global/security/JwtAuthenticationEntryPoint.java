package com.magampick.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.BaseErrorCode;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.exception.ErrorResponse;
import com.magampick.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증 실패(401) 응답을 ApiResponse.error envelope 로 직접 write 한다. Security 필터 체인은 @RestControllerAdvice 범위
 * 밖이라 GlobalExceptionHandler 가 잡지 못한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    BaseErrorCode errorCode = resolveErrorCode(request);
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorResponse.from(errorCode)));
  }

  private BaseErrorCode resolveErrorCode(HttpServletRequest request) {
    Object attr = request.getAttribute(JwtAuthenticationFilter.AUTH_EXCEPTION_ATTRIBUTE);
    if (attr instanceof BusinessException businessException) {
      return businessException.getErrorCode();
    }
    return CommonErrorCode.UNAUTHORIZED;
  }
}
