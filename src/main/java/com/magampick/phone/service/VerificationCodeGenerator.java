package com.magampick.phone.service;

/** 6자리 OTP 인증번호 생성 추상화. 테스트에서 결정적 코드로 stub 하기 위해 분리한다. */
public interface VerificationCodeGenerator {

  String generate();
}
