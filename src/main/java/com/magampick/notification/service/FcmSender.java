package com.magampick.notification.service;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FCM 푸시 발송. {@link FirebaseMessaging} 빈은 mock 모드(app.fcm.mock-enabled=true)에서 생성되지 않으므로({@link
 * FirebaseConfig}) Optional 로 주입받는다 — 빈이 없으면 실발송 없이 로그만 남긴다(SOLAPI mock 패턴과 동일).
 *
 * <p><b>data-only 발송:</b> notification 블록 없이 {@code data} payload 만 실어 보낸다. notification+data 동시 전송
 * 시 브라우저 자동표시와 SW {@code onBackgroundMessage} 가 겹쳐 알림이 중복 표시될 수 있어, 표시·클릭을 SW
 * (firebase-messaging-sw.js)가 단독 제어하도록 data-only 로 통일한다. 프론트는 {@code category} 로 라우팅하고 {@code
 * notificationId} 로 읽음 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSender {

  private final Optional<FirebaseMessaging> firebaseMessaging;

  /**
   * 푸시 data payload 표준 계약 — 프론트(SW)가 이 키들에 의존한다. {@code category} 는 소문자, 비어있는 {@code
   * notificationId}/{@code link} 는 빈 문자열로 채운다(FCM data 값은 null 불가).
   *
   * @param title 알림 제목
   * @param body 알림 본문
   * @param category 알림 카테고리 — 프론트 라우팅 기준
   * @param notificationId 저장된 알림 ID (없으면 {@code ""})
   * @param link 연결 링크 (없으면 {@code ""})
   */
  public static Map<String, String> dataOf(
      String title, String body, NotificationCategory category, Long notificationId, String link) {
    Map<String, String> data = new HashMap<>();
    data.put("title", title);
    data.put("body", body);
    data.put("category", category.name().toLowerCase(Locale.ROOT));
    data.put("notificationId", notificationId == null ? "" : String.valueOf(notificationId));
    data.put("link", link == null ? "" : link);
    return data;
  }

  /**
   * 단일 토큰으로 푸시 1건 발송. data-only — {@link #dataOf} 로 만든 payload 를 그대로 싣는다.
   *
   * @return FCM 메시지 ID (mock 모드면 {@code "MOCK"})
   * @throws BusinessException FCM_SEND_FAILED — 발송 실패 시
   */
  public String send(String token, Map<String, String> data) {
    if (firebaseMessaging.isEmpty()) {
      log.info("[MOCK FCM] 푸시 발송. token={}, title={}", token, data.get("title"));
      return "MOCK";
    }
    Message message = Message.builder().setToken(token).putAllData(data).build();
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
   * 여러 토큰으로 멀티캐스트 발송. data-only — {@link #dataOf} 로 만든 payload 를 그대로 싣는다. 개별 토큰 실패는 배치 응답으로 수집하고,
   * UNREGISTERED/INVALID_ARGUMENT 로 실패한 토큰은 정리 대상(deadTokens)으로 반환한다.
   *
   * @return 성공 수 + 죽은 토큰 목록
   * @throws BusinessException FCM_SEND_FAILED — 배치 호출 자체가 실패 시
   */
  public FcmSendResult sendEachToTokens(List<String> tokens, Map<String, String> data) {
    if (firebaseMessaging.isEmpty()) {
      log.info("[MOCK FCM] 멀티캐스트 발송. count={}, title={}", tokens.size(), data.get("title"));
      return new FcmSendResult(0, List.of());
    }
    MulticastMessage message =
        MulticastMessage.builder().addAllTokens(tokens).putAllData(data).build();
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
