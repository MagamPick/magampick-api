package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

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
class CustomerNotificationQueryServiceTest {

  @Mock NotificationRepository notificationRepository;
  @InjectMocks CustomerNotificationQueryService queryService;

  private static final Long CUSTOMER_ID = 1L;

  private Notification stubNotification(Long id, NotificationCategory category) {
    Notification n = Notification.create(Role.CUSTOMER, CUSTOMER_ID, category, "제목", "내용", null);
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
            stubNotification(2L, NotificationCategory.DEAL));
    given(
            notificationRepository.findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
                Role.CUSTOMER, CUSTOMER_ID))
        .willReturn(notifications);

    // when
    NotificationListResponse response = queryService.list(CUSTOMER_ID, "all");

    // then
    assertThat(response.items()).hasSize(2);
  }

  @Test
  void 알림_목록_deal_세그먼트_조회() {
    // given
    List<Notification> notifications = List.of(stubNotification(1L, NotificationCategory.DEAL));
    given(
            notificationRepository.findByReceiverTypeAndReceiverIdAndCategoryOrderByCreatedAtDesc(
                Role.CUSTOMER, CUSTOMER_ID, NotificationCategory.DEAL))
        .willReturn(notifications);

    // when
    NotificationListResponse response = queryService.list(CUSTOMER_ID, "deal");

    // then
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).category()).isEqualTo("deal");
  }

  @Test
  void 알림_목록_order_세그먼트_조회() {
    // given
    List<Notification> notifications = List.of(stubNotification(2L, NotificationCategory.ORDER));
    given(
            notificationRepository.findByReceiverTypeAndReceiverIdAndCategoryOrderByCreatedAtDesc(
                Role.CUSTOMER, CUSTOMER_ID, NotificationCategory.ORDER))
        .willReturn(notifications);

    // when
    NotificationListResponse response = queryService.list(CUSTOMER_ID, "order");

    // then
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).category()).isEqualTo("order");
  }

  @Test
  void 알림_목록_segment_null이면_전체_조회() {
    // given
    given(
            notificationRepository.findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
                Role.CUSTOMER, CUSTOMER_ID))
        .willReturn(List.of());

    // when
    NotificationListResponse response = queryService.list(CUSTOMER_ID, null);

    // then
    verify(notificationRepository)
        .findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(Role.CUSTOMER, CUSTOMER_ID);
    verify(notificationRepository, never())
        .findByReceiverTypeAndReceiverIdAndCategoryOrderByCreatedAtDesc(any(), any(), any());
  }

  // ── unreadCount ───────────────────────────────────────────────────────────────

  @Test
  void 미읽음_알림_수_조회_성공() {
    // given
    given(
            notificationRepository.countByReceiverTypeAndReceiverIdAndIsReadFalse(
                Role.CUSTOMER, CUSTOMER_ID))
        .willReturn(3L);

    // when
    UnreadCountResponse response = queryService.unreadCount(CUSTOMER_ID);

    // then
    assertThat(response.count()).isEqualTo(3L);
  }

  // ── markRead ──────────────────────────────────────────────────────────────────

  @Test
  void 알림_읽음_처리_성공() {
    // given
    Notification notification = stubNotification(10L, NotificationCategory.ORDER);
    given(
            notificationRepository.findByIdAndReceiverTypeAndReceiverId(
                10L, Role.CUSTOMER, CUSTOMER_ID))
        .willReturn(Optional.of(notification));

    // when
    queryService.markRead(CUSTOMER_ID, 10L);

    // then
    assertThat(notification.isRead()).isTrue();
  }

  @Test
  void 존재하지않는_알림_읽음처리_실패() {
    // given
    given(
            notificationRepository.findByIdAndReceiverTypeAndReceiverId(
                999L, Role.CUSTOMER, CUSTOMER_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> queryService.markRead(CUSTOMER_ID, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", NotificationErrorCode.NOTIFICATION_NOT_FOUND);
  }

  // ── markAllRead ───────────────────────────────────────────────────────────────

  @Test
  void 알림_전체_읽음_처리_성공() {
    // when
    queryService.markAllRead(CUSTOMER_ID);

    // then
    verify(notificationRepository).markAllRead(Role.CUSTOMER, CUSTOMER_ID);
  }
}
