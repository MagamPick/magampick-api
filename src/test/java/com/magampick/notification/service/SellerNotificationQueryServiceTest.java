package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.security.Role;
import com.magampick.notification.domain.Notification;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.dto.NotificationListResponse;
import com.magampick.notification.dto.UnreadCountResponse;
import com.magampick.notification.exception.NotificationErrorCode;
import com.magampick.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerNotificationQueryServiceTest {

  @Mock NotificationRepository notificationRepository;
  @InjectMocks SellerNotificationQueryService queryService;

  private static final Long SELLER_ID = 2L;

  private Notification stubNotification(Long id, NotificationCategory category) {
    Notification n = Notification.create(Role.SELLER, SELLER_ID, category, "제목", "내용", null);
    ReflectionTestUtils.setField(n, "id", id);
    ReflectionTestUtils.setField(n, "createdAt", java.time.LocalDateTime.now());
    return n;
  }

  // ── list ──────────────────────────────────────────────────────────────────────

  @Test
  void 알림_목록_전체_조회_성공() {
    // given
    List<Notification> notifications =
        List.of(
            stubNotification(1L, NotificationCategory.ORDER),
            stubNotification(2L, NotificationCategory.NOTICE));
    given(
            notificationRepository.findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
                Role.SELLER, SELLER_ID))
        .willReturn(notifications);

    // when
    NotificationListResponse response = queryService.list(SELLER_ID);

    // then
    assertThat(response.items()).hasSize(2);
  }

  @Test
  void 알림_목록_조회_결과_없음() {
    // given
    given(
            notificationRepository.findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
                Role.SELLER, SELLER_ID))
        .willReturn(List.of());

    // when
    NotificationListResponse response = queryService.list(SELLER_ID);

    // then
    assertThat(response.items()).isEmpty();
  }

  // ── unreadCount ───────────────────────────────────────────────────────────────

  @Test
  void 미읽음_알림_수_조회_성공() {
    // given
    given(
            notificationRepository.countByReceiverTypeAndReceiverIdAndIsReadFalse(
                Role.SELLER, SELLER_ID))
        .willReturn(5L);

    // when
    UnreadCountResponse response = queryService.unreadCount(SELLER_ID);

    // then
    assertThat(response.count()).isEqualTo(5L);
  }

  // ── markRead ──────────────────────────────────────────────────────────────────

  @Test
  void 알림_읽음_처리_성공() {
    // given
    Notification notification = stubNotification(10L, NotificationCategory.ORDER);
    given(notificationRepository.findByIdAndReceiverTypeAndReceiverId(10L, Role.SELLER, SELLER_ID))
        .willReturn(Optional.of(notification));

    // when
    queryService.markRead(SELLER_ID, 10L);

    // then
    assertThat(notification.isRead()).isTrue();
  }

  @Test
  void 존재하지않는_알림_읽음처리_실패() {
    // given
    given(notificationRepository.findByIdAndReceiverTypeAndReceiverId(999L, Role.SELLER, SELLER_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> queryService.markRead(SELLER_ID, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.NOTIFICATION_NOT_FOUND);
  }

  // ── markAllRead ───────────────────────────────────────────────────────────────

  @Test
  void 알림_전체_읽음_처리_성공() {
    // when
    queryService.markAllRead(SELLER_ID);

    // then
    verify(notificationRepository).markAllRead(Role.SELLER, SELLER_ID);
  }
}
