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
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.exception.NotificationErrorCode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmSenderTest {

  @Mock FirebaseMessaging firebaseMessaging;

  private static final Map<String, String> DATA =
      FcmSender.dataOf("제목", "본문", NotificationCategory.ORDER, 7L, "/orders/1");

  // ── data payload 계약 (dataOf) ───────────────────────────────────────────────

  @Test
  void dataOf_표준_5키_payload_생성_category는_소문자_id는_문자열() {
    Map<String, String> data =
        FcmSender.dataOf("제목", "본문", NotificationCategory.BENEFIT, 42L, "/benefits");

    assertThat(data)
        .containsEntry("title", "제목")
        .containsEntry("body", "본문")
        .containsEntry("category", "benefit")
        .containsEntry("notificationId", "42")
        .containsEntry("link", "/benefits");
  }

  @Test
  void dataOf_notificationId_link_null이면_빈_문자열로_채움() {
    Map<String, String> data =
        FcmSender.dataOf("제목", "본문", NotificationCategory.SYSTEM, null, null);

    assertThat(data).containsEntry("notificationId", "").containsEntry("link", "");
  }

  // ── 단일 발송 (send) ──────────────────────────────────────────────────────────

  @Test
  void mock_모드_FirebaseMessaging_빈_없으면_실발송_없이_MOCK_반환() {
    // given — mock 모드(=FirebaseConfig 비활성)에서는 FirebaseMessaging 빈이 없다
    FcmSender sender = new FcmSender(Optional.empty());

    // when
    String result = sender.send("token-1", DATA);

    // then
    assertThat(result).isEqualTo("MOCK");
  }

  @Test
  void 실발송_모드에서_data_payload_담아_send_호출하고_messageId_반환() throws Exception {
    // given
    FcmSender sender = new FcmSender(Optional.of(firebaseMessaging));
    given(firebaseMessaging.send(any(Message.class))).willReturn("projects/x/messages/1");

    // when
    String result = sender.send("token-1", DATA);

    // then
    assertThat(result).isEqualTo("projects/x/messages/1");
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(firebaseMessaging).send(captor.capture());
    assertThat(dataField(captor.getValue())).containsAllEntriesOf(DATA);
    assertThat(field(captor.getValue(), "notification")).isNull(); // data-only
  }

  @Test
  void FCM_발송_실패시_FCM_SEND_FAILED_BusinessException() throws Exception {
    // given
    FcmSender sender = new FcmSender(Optional.of(firebaseMessaging));
    FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
    given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

    // when / then
    assertThatThrownBy(() -> sender.send("token-1", DATA))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.FCM_SEND_FAILED);
  }

  // ── 멀티캐스트 발송 (sendEachToTokens) ────────────────────────────────────────

  @Test
  void 멀티캐스트_mock_모드_빈_없으면_빈_결과() {
    // given
    FcmSender sender = new FcmSender(Optional.empty());

    // when
    FcmSendResult result = sender.sendEachToTokens(List.of("t1", "t2"), DATA);

    // then
    assertThat(result.successCount()).isZero();
    assertThat(result.deadTokens()).isEmpty();
  }

  @Test
  void 멀티캐스트_실발송시_data_payload_담고_UNREGISTERED_토큰을_죽은토큰으로_식별() throws Exception {
    // given — t1 성공, t2 는 UNREGISTERED(죽은 토큰)
    FcmSender sender = new FcmSender(Optional.of(firebaseMessaging));
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
    FcmSendResult result = sender.sendEachToTokens(List.of("t1", "t2"), DATA);

    // then
    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.deadTokens()).containsExactly("t2");
    ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
    verify(firebaseMessaging).sendEachForMulticast(captor.capture());
    assertThat(dataField(captor.getValue())).containsAllEntriesOf(DATA);
    assertThat(field(captor.getValue(), "notification")).isNull(); // data-only
  }

  // ── FCM Message 는 public getter 가 없어, 직렬화 필드를 리플렉션으로 검사 ──────────

  @SuppressWarnings("unchecked")
  private static Map<String, String> dataField(Object message) throws Exception {
    return (Map<String, String>) field(message, "data");
  }

  private static Object field(Object message, String name) throws Exception {
    Field f = message.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(message);
  }
}
