package com.magampick.phone.service;

/** SMS 발송 추상화. 실연동(SOLAPI)과 mock 을 교체 가능하게 한다. 발송 실패 시 RuntimeException 을 던진다. */
public interface SmsSender {

  void sendVerificationCode(String phoneNumber, String code);
}
