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
    Optional<CustomerNotificationSetting> settingOpt =
        customerNotificationSettingRepository.findByCustomerId(customerId);
    if (settingOpt.isEmpty()) return;

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

    notificationRepository.save(
        Notification.create(Role.CUSTOMER, customerId, category, title, body, link));
    try {
      sendToOwner(Role.CUSTOMER, customerId, title, body);
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
    Optional<SellerNotificationSetting> settingOpt =
        sellerNotificationSettingRepository.findBySellerId(sellerId);
    if (settingOpt.isEmpty()) return;

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

    notificationRepository.save(
        Notification.create(Role.SELLER, sellerId, category, title, body, link));
    try {
      sendToOwner(Role.SELLER, sellerId, title, body);
    } catch (Exception e) {
      log.warn("사장 FCM 발송 실패. sellerId={}, title={}", sellerId, title, e);
    }
  }

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
