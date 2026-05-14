package com.magampick.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.CommonErrorCode;
import com.magampick.global.exception.ErrorResponse;
import com.magampick.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 인가 실패(403) 응답을 ApiResponse.error envelope 로 직접 write 한다. */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(CommonErrorCode.FORBIDDEN.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getWriter(), ApiResponse.error(ErrorResponse.from(CommonErrorCode.FORBIDDEN)));
  }
}
