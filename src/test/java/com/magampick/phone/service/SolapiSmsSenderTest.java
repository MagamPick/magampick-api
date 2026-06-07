package com.magampick.phone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolapiSmsSenderTest {

  private static final String SENDER_NUMBER = "01099998888";

  @Mock private DefaultMessageService messageService;
  @Mock private SmsConfig smsConfig;

  private SolapiSmsSender sender;

  @BeforeEach
  void setUp() {
    SolapiProperties properties =
        new SolapiProperties("api-key", "api-secret", SENDER_NUMBER, "https://api.solapi.com");
    sender = new SolapiSmsSender(smsConfig, Optional.of(messageService), Optional.of(properties));
  }

  @Test
  void mock_모드에서는_SOLAPI_호출_없이_로그만_남긴다() {
    given(smsConfig.isMockEnabled()).willReturn(true);

    sender.sendVerificationCode("01011112222", "123456");

    verify(messageService, never()).sendOne(any());
  }

  @Test
  void 실발송_모드에서_등록발신번호_수신번호_코드로_sendOne_을_호출한다() throws Exception {
    given(smsConfig.isMockEnabled()).willReturn(false);
    String phone = "01011112222";
    String code = "123456";

    sender.sendVerificationCode(phone, code);

    ArgumentCaptor<SingleMessageSendingRequest> captor =
        ArgumentCaptor.forClass(SingleMessageSendingRequest.class);
    verify(messageService).sendOne(captor.capture());
    Message sent = captor.getValue().getMessage();
    assertThat(sent.getFrom()).isEqualTo(SENDER_NUMBER);
    assertThat(sent.getTo()).isEqualTo(phone);
    assertThat(sent.getText()).contains(code);
  }

  @Test
  void SOLAPI_발송_실패시_RuntimeException_을_던진다() throws Exception {
    given(smsConfig.isMockEnabled()).willReturn(false);
    given(messageService.sendOne(any())).willThrow(new RuntimeException("SOLAPI down"));

    assertThatThrownBy(() -> sender.sendVerificationCode("01011112222", "123456"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void mock_꺼졌는데_SOLAPI_빈_없으면_RuntimeException() {
    SolapiSmsSender senderWithoutSolapi =
        new SolapiSmsSender(smsConfig, Optional.empty(), Optional.empty());
    given(smsConfig.isMockEnabled()).willReturn(false);

    assertThatThrownBy(() -> senderWithoutSolapi.sendVerificationCode("01011112222", "123456"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("재시작");
  }
}
