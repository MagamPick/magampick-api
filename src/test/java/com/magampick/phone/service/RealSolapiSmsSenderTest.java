package com.magampick.phone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
class RealSolapiSmsSenderTest {

  private static final String SENDER_NUMBER = "01099998888";

  @Mock private DefaultMessageService messageService;
  private RealSolapiSmsSender sender;

  @BeforeEach
  void setUp() {
    SolapiProperties properties =
        new SolapiProperties("api-key", "api-secret", SENDER_NUMBER, "https://api.solapi.com");
    sender = new RealSolapiSmsSender(messageService, properties);
  }

  @Test
  void 인증번호_발송시_등록발신번호와_수신번호_코드로_sendOne_을_호출한다() throws Exception {
    // given
    String phone = "01011112222";
    String code = "123456";

    // when
    sender.sendVerificationCode(phone, code);

    // then
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
    // given
    given(messageService.sendOne(any())).willThrow(new RuntimeException("SOLAPI down"));

    // when / then
    assertThatThrownBy(() -> sender.sendVerificationCode("01011112222", "123456"))
        .isInstanceOf(RuntimeException.class);
  }
}
