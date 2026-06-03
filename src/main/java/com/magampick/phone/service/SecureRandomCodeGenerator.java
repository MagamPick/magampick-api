package com.magampick.phone.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** SecureRandom 기반 6자리 숫자 OTP 생성기. */
@Component
public class SecureRandomCodeGenerator implements VerificationCodeGenerator {

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    return String.format("%06d", random.nextInt(1_000_000));
  }
}
