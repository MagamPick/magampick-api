package com.magampick.global.support;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용 컨트롤러 — cross-cutting (응답 wrap / 예외 처리 / 인증) 동작 검증용. */
@RestController
@RequestMapping("/test-support")
public class CrossCuttingTestController {

  public record Echo(String value) {}

  public record EchoRequest(@NotBlank String value) {}

  @GetMapping("/ok")
  public Echo ok() {
    return new Echo("hello");
  }

  @PostMapping("/validate")
  public Echo validate(@Valid @RequestBody EchoRequest request) {
    return new Echo(request.value());
  }

  @GetMapping("/business-error")
  public Echo businessError() {
    throw new BusinessException(CommonErrorCode.FORBIDDEN);
  }

  @GetMapping("/unexpected-error")
  public Echo unexpectedError() {
    throw new IllegalStateException("intentional failure for test");
  }
}
