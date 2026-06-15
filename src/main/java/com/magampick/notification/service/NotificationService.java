package com.magampick.notification.service;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.CustomerNotificationSetting;
import com.magampick.notification.domain.Notification;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.domain.PushToken;
import com.magampick.notification.domain.SellerNotificationSetting;
import com.magampick.notification.repository.CustomerNotificationSettingRepository;
import com.magampick.notification.repository.NotificationRepository;
import com.magampick.notification.repository.PushTokenRepository;
import com.magampick.notification.repository.SellerNotificationSettingRepository;
import java.util.List;
import java.util.Optional;
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
  private final NotificationRepository notificationRepository;
  private final CustomerNotificationSettingRepository customerNotificationSettingRepository;
  private final SellerNotificationSettingRepository sellerNotificationSettingRepository;

  /**
   * 소비자에게 알림 발송. 설정 조회 → OFF 면 skip → DB 저장 → FCM 발송. FCM 예외는 로그만 남기고 전파하지 않는다.
   *
   * @param customerId 수신 소비자 ID
   * @param settingKey 설정 키 (camelCase 필드명)
   * @param category 알림 카테고리
   */
  @Transactional
  public void notifyCustomer(
      Long customerId,
      String settingKey,
      NotificationCategory category,
      String title,
      String body,
      String link) {
    // 알림 설정 조회
    Optional<CustomerNotificationSetting> settingOpt =
        customerNotificationSettingRepository.findByCustomerId(customerId);
    if (settingOpt.isEmpty()) return;

    // 설정 키 활성화 여부 확인
    CustomerNotificationSetting setting = settingOpt.get();
    boolean enabled =
        switch (settingKey) {
          case "nearbyDeal" -> setting.isNearbyDeal();
          case "favoriteStore" -> setting.isFavoriteStore();
          case "orderRefund" -> setting.isOrderRefund();
          case "reviewReply" -> setting.isReviewReply();
          case "eventBenefit" -> setting.isEventBenefit();
          case "marketing" -> setting.isMarketing();
          default -> false;
        };
    if (!enabled) return;

    // 알림 저장
    Notification saved =
        notificationRepository.save(
            Notification.create(Role.CUSTOMER, customerId, category, title, body, link));
    // FCM 발송
    try {
      sendToOwner(Role.CUSTOMER, customerId, category, title, body, link, saved.getId());
    } catch (Exception e) {
      log.warn("소비자 FCM 발송 실패. customerId={}, title={}", customerId, title, e);
    }
  }

  /**
   * 사장에게 알림 발송. 설정 조회 → OFF 면 skip → DB 저장 → FCM 발송. FCM 예외는 로그만 남기고 전파하지 않는다.
   *
   * @param sellerId 수신 사장 ID
   * @param settingKey 설정 키 (camelCase 필드명)
   * @param category 알림 카테고리
   */
  @Transactional
  public void notifySeller(
      Long sellerId,
      String settingKey,
      NotificationCategory category,
      String title,
      String body,
      String link) {
    // 알림 설정 조회
    Optional<SellerNotificationSetting> settingOpt =
        sellerNotificationSettingRepository.findBySellerId(sellerId);
    if (settingOpt.isEmpty()) return;

    // 설정 키 활성화 여부 확인
    SellerNotificationSetting setting = settingOpt.get();
    boolean enabled =
        switch (settingKey) {
          case "newOrder" -> setting.isNewOrder();
          case "orderCancel" -> setting.isOrderCancel();
          case "refundRequest" -> setting.isRefundRequest();
          case "newReview" -> setting.isNewReview();
          case "notice" -> setting.isNotice();
          case "marketing" -> setting.isMarketing();
          default -> false;
        };
    if (!enabled) return;

    // 알림 저장
    Notification saved =
        notificationRepository.save(
            Notification.create(Role.SELLER, sellerId, category, title, body, link));
    // FCM 발송
    try {
      sendToOwner(Role.SELLER, sellerId, category, title, body, link, saved.getId());
    } catch (Exception e) {
      log.warn("사장 FCM 발송 실패. sellerId={}, title={}", sellerId, title, e);
    }
  }

  /**
   * 토글 무시 always-on 알림 (거래성). Notification 저장 + FCM 발송.
   *
   * <p>설정 검사 없이 무조건 DB 에 알림을 저장하고 FCM 을 발송한다. FCM 실패는 로그만 남기고 전파하지 않는다.
   *
   * @param ownerType 수신자 역할 (CUSTOMER 또는 SELLER)
   * @param ownerId 수신자 ID
   * @param category 알림 카테고리
   * @param title 알림 제목
   * @param body 알림 본문
   * @param link 연결 링크
   */
  @Transactional
  public void notifyAlways(
      Role ownerType,
      Long ownerId,
      NotificationCategory category,
      String title,
      String body,
      String link) {
    // 알림 저장
    Notification saved =
        notificationRepository.save(
            Notification.create(ownerType, ownerId, category, title, body, link));
    // FCM 발송
    try {
      sendToOwner(ownerType, ownerId, category, title, body, link, saved.getId());
    } catch (Exception e) {
      log.warn("always-on 알림 FCM 실패. ownerType={}, ownerId={}", ownerType, ownerId, e);
    }
  }

  /**
   * 한 사용자의 모든 디바이스로 푸시 발송. data-only payload({@link FcmSender#dataOf})를 실어 보내고, 발송 후 죽은
   * 토큰(UNREGISTERED/INVALID_ARGUMENT)은 정리한다.
   *
   * @param category 알림 카테고리 — 프론트 라우팅 기준 (data payload)
   * @param link 연결 링크 — 없으면 빈 문자열로 실림 (data payload)
   * @param notificationId 저장된 알림 ID — 클릭 시 읽음 처리용 (data payload). 영속 전이면 null 가능
   * @return 발송 성공한 토큰 수
   */
  @Transactional
  public int sendToOwner(
      Role ownerType,
      Long ownerId,
      NotificationCategory category,
      String title,
      String body,
      String link,
      Long notificationId) {
    // 토큰 조회
    List<PushToken> tokens = pushTokenRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
    if (tokens.isEmpty()) {
      log.info("발송 대상 토큰 없음. ownerType={}, ownerId={}", ownerType, ownerId);
      return 0;
    }
    // FCM 멀티캐스트 발송
    List<String> tokenValues = tokens.stream().map(PushToken::getToken).toList();
    FcmSendResult result =
        fcmSender.sendEachToTokens(
            tokenValues, FcmSender.dataOf(title, body, category, notificationId, link));
    // 죽은 토큰 정리
    if (!result.deadTokens().isEmpty()) {
      pushTokenRepository.deleteByTokenIn(result.deadTokens());
      log.info("죽은 FCM 토큰 정리됨. count={}", result.deadTokens().size());
    }
    return result.successCount();
  }
}
