package com.magampick.phone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * SOLAPI 실 SMS 발송 ({@code @Profile("!test")} — test 외 모든 환경). 등록 발신번호로 인증번호를 발송하고, 실패 시
 * RuntimeException 을 던져 호출 측({@link PhoneVerificationService})이 {@code SMS_SEND_FAILED} 로 매핑하게 한다.
 */
@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class RealSolapiSmsSender implements SmsSender {

  private final DefaultMessageService messageService;
  private final SolapiProperties properties;

  @Override
  public void sendVerificationCode(String phoneNumber, String code) {
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
