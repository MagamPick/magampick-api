package com.magampick.phone.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Service;

/**
 * SMS 발송 구현체. {@link SmsConfig#isMockEnabled()} 가 true 면 실제 발송 없이 로그만 남긴다. false 이면 SOLAPI 로 실발송.
 *
 * <p>SOLAPI 빈({@link DefaultMessageService})은 {@code app.sms.mock-enabled=false} 일 때만 생성({@link
 * SolapiConfig})되므로, mock 모드로 시작한 서버에서 런타임에 mock=false 로 전환하면 빈이 없어 발송 실패한다 — 재시작 필요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolapiSmsSender implements SmsSender {

  private final SmsConfig smsConfig;
  private final Optional<DefaultMessageService> messageService;
  private final Optional<SolapiProperties> properties;

  @Override
  public void sendVerificationCode(String phoneNumber, String code) {
    if (smsConfig.isMockEnabled()) {
      log.info("[MOCK SMS] 본인인증 번호 발송. phoneNumber={}, code={}", phoneNumber, code);
      return;
    }
    DefaultMessageService svc =
        messageService.orElseThrow(
            () -> new RuntimeException("SOLAPI 빈 없음 — app.sms.mock-enabled=false 로 서버를 재시작하세요"));
    SolapiProperties props =
        properties.orElseThrow(
            () -> new RuntimeException("SOLAPI 빈 없음 — app.sms.mock-enabled=false 로 서버를 재시작하세요"));

    Message message = new Message();
    message.setFrom(props.senderNumber());
    message.setTo(phoneNumber);
    message.setText("[마감픽] 인증번호 " + code + " (3분 이내 입력)");
    try {
      svc.sendOne(new SingleMessageSendingRequest(message));
    } catch (Exception e) {
      log.warn("SOLAPI SMS 발송 실패. to={}", phoneNumber, e);
      throw new RuntimeException("SOLAPI SMS 발송 실패", e);
    }
  }
}
