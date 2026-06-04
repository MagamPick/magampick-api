package com.magampick.phone.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * test 프로파일 전용 SMS 발송 mock — 실제 발송 대신 로그로 인증번호를 남긴다. test 외 모든 환경(local/dev/prod)은 {@link
 * RealSolapiSmsSender}(SOLAPI 실발송)를 쓴다.
 */
@Slf4j
@Service
@Profile("test")
public class MockSmsSender implements SmsSender {

  @Override
  public void sendVerificationCode(String phoneNumber, String code) {
    log.info("[MOCK SMS] 본인인증 번호 발송. phoneNumber={}, code={}", phoneNumber, code);
  }
}
