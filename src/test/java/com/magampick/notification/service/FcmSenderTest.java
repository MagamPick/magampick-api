package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.exception.NotificationErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmSenderTest {

  @Mock FirebaseMessaging firebaseMessaging;

  // ── 단일 발송 (send) ──────────────────────────────────────────────────────────

  @Test
  void mock_모드_FirebaseMessaging_빈_없으면_실발송_없이_MOCK_반환() {
    // given — mock 모드(=FirebaseConfig 비활성)에서는 FirebaseMessaging 빈이 없다
    FcmSender sender = new FcmSender(java.util.Optional.empty());

    // when
    String result = sender.send("token-1", "제목", "본문");

    // then
    assertThat(result).isEqualTo("MOCK");
  }

  @Test
  void 실발송_모드에서_FirebaseMessaging_send_호출하고_messageId_반환() throws Exception {
    // given
    FcmSender sender = new FcmSender(java.util.Optional.of(firebaseMessaging));
    given(firebaseMessaging.send(any(Message.class))).willReturn("projects/x/messages/1");

    // when
    String result = sender.send("token-1", "제목", "본문");

    // then
    assertThat(result).isEqualTo("projects/x/messages/1");
    verify(firebaseMessaging).send(any(Message.class));
  }

  @Test
  void FCM_발송_실패시_FCM_SEND_FAILED_BusinessException() throws Exception {
    // given
    FcmSender sender = new FcmSender(java.util.Optional.of(firebaseMessaging));
    FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
    given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

    // when / then
    assertThatThrownBy(() -> sender.send("token-1", "제목", "본문"))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.FCM_SEND_FAILED);
  }

  // ── 멀티캐스트 발송 (sendEachToTokens) ────────────────────────────────────────

  @Test
  void 멀티캐스트_mock_모드_빈_없으면_빈_결과() {
    // given
    FcmSender sender = new FcmSender(java.util.Optional.empty());

    // when
    FcmSendResult result = sender.sendEachToTokens(List.of("t1", "t2"), "제목", "본문");

    // then
    assertThat(result.successCount()).isZero();
    assertThat(result.deadTokens()).isEmpty();
  }

  @Test
  void 멀티캐스트_실발송시_UNREGISTERED_토큰을_죽은토큰으로_식별() throws Exception {
    // given — t1 성공, t2 는 UNREGISTERED(죽은 토큰)
    FcmSender sender = new FcmSender(java.util.Optional.of(firebaseMessaging));
    SendResponse ok = mock(SendResponse.class);
    given(ok.isSuccessful()).willReturn(true);
    FirebaseMessagingException deadEx = mock(FirebaseMessagingException.class);
    given(deadEx.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
    SendResponse dead = mock(SendResponse.class);
    given(dead.isSuccessful()).willReturn(false);
    given(dead.getException()).willReturn(deadEx);
    BatchResponse batch = mock(BatchResponse.class);
    given(batch.getResponses()).willReturn(List.of(ok, dead));
    given(batch.getSuccessCount()).willReturn(1);
    given(batch.getFailureCount()).willReturn(1);
    given(firebaseMessaging.sendEachForMulticast(any())).willReturn(batch);

    // when
    FcmSendResult result = sender.sendEachToTokens(List.of("t1", "t2"), "제목", "본문");

    // then
    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.deadTokens()).containsExactly("t2");
  }
}
