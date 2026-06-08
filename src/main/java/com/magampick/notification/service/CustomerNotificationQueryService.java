package com.magampick.notification.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.Role;
import com.magampick.notification.domain.Notification;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.dto.NotificationListResponse;
import com.magampick.notification.dto.NotificationResponse;
import com.magampick.notification.dto.UnreadCountResponse;
import com.magampick.notification.exception.NotificationErrorCode;
import com.magampick.notification.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 알림 수신함 조회 / 읽음 처리. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerNotificationQueryService {

  private final NotificationRepository notificationRepository;

  /**
   * 소비자 알림 목록 조회.
   *
   * @param segment "deal" → DEAL, "order" → ORDER, 그 외(null/blank/"all") → 전체
   */
  public NotificationListResponse list(Long customerId, String segment) {
    List<Notification> notifications;
    if ("deal".equalsIgnoreCase(segment)) {
      notifications =
          notificationRepository.findByReceiverTypeAndReceiverIdAndCategoryOrderByCreatedAtDesc(
              Role.CUSTOMER, customerId, NotificationCategory.DEAL);
    } else if ("order".equalsIgnoreCase(segment)) {
      notifications =
          notificationRepository.findByReceiverTypeAndReceiverIdAndCategoryOrderByCreatedAtDesc(
              Role.CUSTOMER, customerId, NotificationCategory.ORDER);
    } else {
      notifications =
          notificationRepository.findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
              Role.CUSTOMER, customerId);
    }
    return new NotificationListResponse(
        notifications.stream().map(NotificationResponse::from).toList());
  }

  /** 미읽음 알림 수 조회. */
  public UnreadCountResponse unreadCount(Long customerId) {
    long count =
        notificationRepository.countByReceiverTypeAndReceiverIdAndIsReadFalse(
            Role.CUSTOMER, customerId);
    return new UnreadCountResponse(count);
  }

  /** 단건 알림 읽음 처리. 본인 알림이 아니면 NOTIFICATION_NOT_FOUND(404). */
  @Transactional
  public void markRead(Long customerId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndReceiverTypeAndReceiverId(notificationId, Role.CUSTOMER, customerId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    notification.markAsRead();
    log.info("소비자 알림 읽음 처리됨. customerId={}, notificationId={}", customerId, notificationId);
  }

  /** 전체 알림 읽음 처리. */
  @Transactional
  public void markAllRead(Long customerId) {
    notificationRepository.markAllRead(Role.CUSTOMER, customerId);
    log.info("소비자 알림 전체 읽음 처리됨. customerId={}", customerId);
  }
}
