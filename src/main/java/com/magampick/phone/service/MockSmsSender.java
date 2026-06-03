package com.magampick.phone.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** SOLAPI 실연동 전까지 사용하는 SMS 발송 mock. 실제 발송 대신 로그로 인증번호를 남긴다. */
@Slf4j
@Service
@Profile("!prod")
public class MockSmsSender implements SmsSender {

  @Override
  public void sendVerificationCode(String phoneNumber, String code) {
    log.info("[MOCK SMS] 본인인증 번호 발송. phoneNumber={}, code={}", phoneNumber, code);
  }
}
