package com.magampick.phone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Service;

/**
 * SMS 발송 구현체. {@link SmsConfig#isMockEnabled()} 가 true 면 실제 발송 없이 로그만 남긴다. false 이면 SOLAPI 로 실발송.
 * SOLAPI 빈은 항상 생성 — 런타임 토글({@link SmsAdminController})이 양방향으로 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolapiSmsSender implements SmsSender {

  private final SmsConfig smsConfig;
  private final DefaultMessageService messageService;
  private final SolapiProperties properties;

  @Override
  public void sendVerificationCode(String phoneNumber, String code) {
    if (smsConfig.isMockEnabled()) {
      log.info("[MOCK SMS] 본인인증 번호 발송. phoneNumber={}, code={}", phoneNumber, code);
      return;
    }
    Message message = new Message();
    message.setFrom(properties.senderNumber());
    message.setTo(phoneNumber);
    message.setText("[마감픽] 인증번호 " + code + " (3분 이내 입력)");
    try {
      messageService.sendOne(new SingleMessageSendingRequest(message));
    } catch (Exception e) {
      log.warn("SOLAPI SMS 발송 실패. to={}", phoneNumber, e);
      throw new RuntimeException("SOLAPI SMS 발송 실패", e);
    }
  }
}
