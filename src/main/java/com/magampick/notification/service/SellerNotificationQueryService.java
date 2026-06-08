package com.magampick.notification.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.Role;
import com.magampick.notification.domain.Notification;
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

/** 사장 알림 수신함 조회 / 읽음 처리. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerNotificationQueryService {

  private final NotificationRepository notificationRepository;

  /** 사장 알림 목록 전체 조회. */
  public NotificationListResponse list(Long sellerId) {
    List<Notification> notifications =
        notificationRepository.findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
            Role.SELLER, sellerId);
    return new NotificationListResponse(
        notifications.stream().map(NotificationResponse::from).toList());
  }

  /** 미읽음 알림 수 조회. */
  public UnreadCountResponse unreadCount(Long sellerId) {
    long count =
        notificationRepository.countByReceiverTypeAndReceiverIdAndIsReadFalse(
            Role.SELLER, sellerId);
    return new UnreadCountResponse(count);
  }

  /** 단건 알림 읽음 처리. 본인 알림이 아니면 NOTIFICATION_NOT_FOUND(404). */
  @Transactional
  public void markRead(Long sellerId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndReceiverTypeAndReceiverId(notificationId, Role.SELLER, sellerId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    notification.markAsRead();
    log.info("사장 알림 읽음 처리됨. sellerId={}, notificationId={}", sellerId, notificationId);
  }

  /** 전체 알림 읽음 처리. */
  @Transactional
  public void markAllRead(Long sellerId) {
    notificationRepository.markAllRead(Role.SELLER, sellerId);
    log.info("사장 알림 전체 읽음 처리됨. sellerId={}", sellerId);
  }
}
