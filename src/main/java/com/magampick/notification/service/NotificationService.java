package com.magampick.notification.service;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.PushToken;
import com.magampick.notification.repository.PushTokenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자에게 푸시 알림 발송. 소유자 토큰 조회 → FCM 멀티캐스트 → 죽은 토큰 정리. 도메인 이벤트(주문 상태 등)에서 호출한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private final PushTokenRepository pushTokenRepository;
  private final FcmSender fcmSender;

  /**
   * 한 사용자의 모든 디바이스로 푸시 발송. 발송 후 죽은 토큰(UNREGISTERED/INVALID_ARGUMENT)은 정리한다.
   *
   * @return 발송 성공한 토큰 수
   */
  @Transactional
  public int sendToOwner(Role ownerType, Long ownerId, String title, String body) {
    List<PushToken> tokens = pushTokenRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
    if (tokens.isEmpty()) {
      log.info("발송 대상 토큰 없음. ownerType={}, ownerId={}", ownerType, ownerId);
      return 0;
    }
    List<String> tokenValues = tokens.stream().map(PushToken::getToken).toList();
    FcmSendResult result = fcmSender.sendEachToTokens(tokenValues, title, body);
    if (!result.deadTokens().isEmpty()) {
      pushTokenRepository.deleteByTokenIn(result.deadTokens());
      log.info("죽은 FCM 토큰 정리됨. count={}", result.deadTokens().size());
    }
    return result.successCount();
  }
}
