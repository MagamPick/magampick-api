package com.magampick.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.exception.NotificationErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FCM 푸시 발송. {@link FirebaseMessaging} 빈은 mock 모드(app.fcm.mock-enabled=true)에서 생성되지 않으므로({@link
 * FirebaseConfig}) Optional 로 주입받는다 — 빈이 없으면 실발송 없이 로그만 남긴다(SOLAPI mock 패턴과 동일).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSender {

  private final Optional<FirebaseMessaging> firebaseMessaging;

  /**
   * 단일 토큰으로 푸시 1건 발송.
   *
   * @return FCM 메시지 ID (mock 모드면 {@code "MOCK"})
   * @throws BusinessException FCM_SEND_FAILED — 발송 실패 시
   */
  public String send(String token, String title, String body) {
    if (firebaseMessaging.isEmpty()) {
      log.info("[MOCK FCM] 푸시 발송. token={}, title={}", token, title);
      return "MOCK";
    }
    Message message =
        Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .build();
    try {
      String messageId = firebaseMessaging.get().send(message);
      log.info("FCM 전송 성공. token={}, messageId={}", token, messageId);
      return messageId;
    } catch (FirebaseMessagingException e) {
      log.warn("FCM 전송 실패. token={}, code={}", token, e.getMessagingErrorCode(), e);
      throw new BusinessException(NotificationErrorCode.FCM_SEND_FAILED);
    }
  }

  /**
   * 여러 토큰으로 멀티캐스트 발송. 개별 토큰 실패는 배치 응답으로 수집하고, UNREGISTERED/INVALID_ARGUMENT 로 실패한 토큰은 정리
   * 대상(deadTokens)으로 반환한다.
   *
   * @return 성공 수 + 죽은 토큰 목록
   * @throws BusinessException FCM_SEND_FAILED — 배치 호출 자체가 실패 시
   */
  public FcmSendResult sendEachToTokens(List<String> tokens, String title, String body) {
    if (firebaseMessaging.isEmpty()) {
      log.info("[MOCK FCM] 멀티캐스트 발송. count={}, title={}", tokens.size(), title);
      return new FcmSendResult(0, List.of());
    }
    MulticastMessage message =
        MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .build();
    try {
      BatchResponse response = firebaseMessaging.get().sendEachForMulticast(message);
      List<SendResponse> responses = response.getResponses();
      List<String> deadTokens = new ArrayList<>();
      for (int i = 0; i < responses.size(); i++) {
        SendResponse r = responses.get(i);
        if (r.isSuccessful()) {
          continue;
        }
        FirebaseMessagingException e = r.getException();
        MessagingErrorCode code = e == null ? null : e.getMessagingErrorCode();
        log.warn("FCM 개별 발송 실패. code={}, message={}", code, e == null ? null : e.getMessage());
        if (code == MessagingErrorCode.UNREGISTERED
            || code == MessagingErrorCode.INVALID_ARGUMENT) {
          deadTokens.add(tokens.get(i));
        }
      }
      log.info(
          "FCM 멀티캐스트 발송. success={}, failure={}",
          response.getSuccessCount(),
          response.getFailureCount());
      return new FcmSendResult(response.getSuccessCount(), deadTokens);
    } catch (FirebaseMessagingException e) {
      log.warn("FCM 멀티캐스트 발송 실패. code={}", e.getMessagingErrorCode(), e);
      throw new BusinessException(NotificationErrorCode.FCM_SEND_FAILED);
    }
  }
}
