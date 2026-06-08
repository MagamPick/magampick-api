package com.magampick.notification.service;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.PushToken;
import com.magampick.notification.dto.PushTokenResponse;
import com.magampick.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FCM 토큰 등록/해제. 등록은 토큰 기준 upsert(소유자 재할당), 해제는 hard delete. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushTokenService {

  private final PushTokenRepository pushTokenRepository;

  /**
   * FCM 토큰 등록 (upsert). 같은 토큰이 이미 있으면 소유자를 재할당하고(기기 공유·재로그인 대응), 없으면 새로 저장한다.
   *
   * @return 등록/갱신된 토큰의 id
   */
  @Transactional
  public PushTokenResponse register(Role ownerType, Long ownerId, String token) {
    PushToken pushToken =
        pushTokenRepository
            .findByToken(token)
            .map(
                existing -> {
                  existing.reassignTo(ownerType, ownerId);
                  return existing;
                })
            .orElseGet(
                () ->
                    pushTokenRepository.save(
                        PushToken.builder()
                            .ownerType(ownerType)
                            .ownerId(ownerId)
                            .token(token)
                            .platform(PushToken.Platform.WEB)
                            .build()));
    log.info("FCM 토큰 등록됨. ownerType={}, ownerId={}", ownerType, ownerId);
    return new PushTokenResponse(pushToken.getId());
  }

  /** FCM 토큰 해제 (로그아웃 등). 미등록 토큰이어도 멱등. */
  @Transactional
  public void unregister(String token) {
    pushTokenRepository.deleteByToken(token);
    log.info("FCM 토큰 해제됨.");
  }
}
