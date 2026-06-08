package com.magampick.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.magampick.coupon.service.CouponService;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.mapper.OrderMapper;
import com.magampick.order.repository.OrderRepository;
import com.magampick.point.service.PointService;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.domain.RefundStatus;
import com.magampick.refund.dto.RefundInfoResponse;
import com.magampick.refund.dto.RefundRejectRequest;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.dto.RefundResponse;
import com.magampick.refund.exception.RefundErrorCode;
import com.magampick.refund.fixture.RefundFixture;
import com.magampick.refund.mapper.RefundMapper;
import com.magampick.refund.repository.RefundRepository;
import com.magampick.store.repository.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

  @Mock OrderRepository orderRepository;
  @Mock RefundRepository refundRepository;
  @Mock StoreRepository storeRepository;
  @Mock OrderMapper orderMapper;
  @Mock RefundMapper refundMapper;
  @Mock PointService pointService;
  @Mock CouponService couponService;
  @Mock NotificationService notificationService;
  @Mock Clock clock;

  @InjectMocks RefundService refundService;

  private static final Long CUSTOMER_ID = 1L;
  private static final Long SELLER_ID = 2L;
  private static final Long ORDER_ID = 42L;
  private static final Long STORE_ID = 10L;
  private static final Long REFUND_ID = 1L;

  @BeforeEach
  void setUpClock() {
    lenient().when(clock.instant()).thenReturn(Instant.now());
    lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 환불 요청
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 환불_요청_성공() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    RefundRequestRequest request = RefundFixture.aRefundRequest();
    Refund savedRefund = RefundFixture.aRequestedRefund(order);
    RefundInfoResponse refundInfo = RefundFixture.aRefundInfoResponse();
    OrderResponse base = OrderFixture.anOrderResponse(ORDER_ID);
    OrderResponse withRefund =
        OrderFixture.anOrderResponse(ORDER_ID); // simplified — just verify withRefund called

    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(refundRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
    given(refundRepository.save(any(Refund.class))).willReturn(savedRefund);
    given(refundMapper.toInfoResponse(savedRefund)).willReturn(refundInfo);
    given(orderMapper.toResponse(order)).willReturn(base);
    given(orderMapper.withRefund(base, refundInfo)).willReturn(withRefund);

    // when
    OrderResponse result = refundService.requestRefund(CUSTOMER_ID, ORDER_ID, request);

    // then
    assertThat(result).isNotNull();
    then(refundRepository).should().save(any(Refund.class));
  }

  @Test
  void 환불_요청_실패_완료되지않은주문() {
    // given
    Order order =
        OrderFixture.anOrderWithStatus(
            OrderFixture.aCustomer(), OrderFixture.aStore(), OrderStatus.PREPARING);
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    // when / then
    assertThatThrownBy(
            () ->
                refundService.requestRefund(CUSTOMER_ID, ORDER_ID, RefundFixture.aRefundRequest()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_NOT_COMPLETED_ORDER));
  }

  @Test
  void 환불_요청_실패_기간초과() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    // completedAt 을 4일 전으로 설정 — REFUND_WINDOW_DAYS(3) 초과
    ReflectionTestUtils.setField(order, "completedAt", LocalDateTime.now().minusDays(4));
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

    // when / then
    assertThatThrownBy(
            () ->
                refundService.requestRefund(CUSTOMER_ID, ORDER_ID, RefundFixture.aRefundRequest()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_WINDOW_EXPIRED));
  }

  @Test
  void 환불_요청_실패_이미요청된주문() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    Refund existing = RefundFixture.aRequestedRefund(order);
    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(refundRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(existing));

    // when / then
    assertThatThrownBy(
            () ->
                refundService.requestRefund(CUSTOMER_ID, ORDER_ID, RefundFixture.aRefundRequest()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_ALREADY_REQUESTED));
  }

  @Test
  void 환불_요청_실패_사유없음() {
    // given — reason 이 빈 문자열인 Request (사유 검증은 orderRepository 조회 이전에 발생)
    RefundRequestRequest emptyReason = new RefundRequestRequest("");

    // when / then
    assertThatThrownBy(() -> refundService.requestRefund(CUSTOMER_ID, ORDER_ID, emptyReason))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_REASON_REQUIRED));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 환불 승인
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 환불_승인_성공() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.aRequestedRefund(order);
    RefundResponse response = RefundFixture.aRefundResponse(REFUND_ID);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);
    given(refundMapper.toResponse(refund)).willReturn(response);

    // when
    RefundResponse result = refundService.approveRefund(SELLER_ID, REFUND_ID);

    // then
    assertThat(result).isNotNull();
    assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
  }

  @Test
  void 환불_승인_실패_이미처리됨() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    Refund approvedRefund = RefundFixture.anApprovedRefund(order);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(approvedRefund));

    // when / then
    assertThatThrownBy(() -> refundService.approveRefund(SELLER_ID, REFUND_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_ALREADY_PROCESSED));
  }

  @Test
  void 환불_승인_실패_본인매장아님() {
    // given — refund 의 order.store.seller.id 가 SELLER_ID 와 다름
    Order order = RefundFixture.aCompletedOrder(); // store seller id = 2L (OrderFixture.aSeller)
    Refund refund = RefundFixture.aRequestedRefund(order);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));

    // when / then — SELLER_ID = 2L, aSeller().id = 2L → 정상이면 OK. 다른 seller 로 테스트
    Long otherSellerId = 99L;
    assertThatThrownBy(() -> refundService.approveRefund(otherSellerId, REFUND_ID))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_FORBIDDEN));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 환불 거부
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 환불_거부_성공() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.aRequestedRefund(order);
    RefundRejectRequest rejectRequest = RefundFixture.aRejectRequest();
    RefundResponse response = RefundFixture.aRefundResponse(REFUND_ID);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);
    given(refundMapper.toResponse(refund)).willReturn(response);

    // when
    RefundResponse result = refundService.rejectRefund(SELLER_ID, REFUND_ID, rejectRequest);

    // then
    assertThat(result).isNotNull();
    assertThat(refund.getStatus()).isEqualTo(RefundStatus.REJECTED);
  }

  @Test
  void 환불_거부_실패_사유없음() {
    // given — 거부 사유 검증은 refundRepository 조회 이전에 발생
    RefundRejectRequest emptyReasonRequest = new RefundRejectRequest("");

    // when / then
    assertThatThrownBy(() -> refundService.rejectRefund(SELLER_ID, REFUND_ID, emptyReasonRequest))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(RefundErrorCode.REFUND_REJECT_REASON_REQUIRED));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 자동 승인 — findAutoApproveTargetIds
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void findAutoApproveTargetIds_반환() {
    // given — REQUESTED + 4일 경과한 환불 2건
    Order order = RefundFixture.aCompletedOrder();
    Refund expired1 = RefundFixture.anExpiredRequestedRefund(order);
    Refund expired2 = RefundFixture.anExpiredRequestedRefund(order);
    ReflectionTestUtils.setField(expired2, "id", 3L);

    given(
            refundRepository.findAllByStatusAndRequestedAtBefore(
                any(RefundStatus.class), any(LocalDateTime.class)))
        .willReturn(List.of(expired1, expired2));

    // when
    List<Long> ids = refundService.findAutoApproveTargetIds();

    // then — anExpiredRequestedRefund 픽스처의 기본 id=2L, 두 번째는 3L 로 덮어씀
    assertThat(ids).containsExactly(2L, 3L);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 자동 승인 — approveAndReverse
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void approveAndReverse_승인_혜택정리() {
    // given — 쿠폰(id=10) + 포인트(300) 적용된 완료 주문
    Order order = RefundFixture.aCompletedOrder();
    ReflectionTestUtils.setField(order, "pointUsed", 300L);
    ReflectionTestUtils.setField(order, "userCouponId", 10L);

    Refund refund = RefundFixture.anExpiredRequestedRefund(order);
    given(refundRepository.findById(2L)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);

    // when
    refundService.approveAndReverse(2L);

    // then
    assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
    assertThat(refund.getResolvedAt()).isNotNull();
    then(refundRepository).should().save(refund);
    then(pointService).should().clawback(order);
    then(pointService).should().restore(eq(order), eq(300L));
    then(couponService).should().restore(10L);
  }

  @Test
  void approveAndReverse_이미처리면_skip() {
    // given — 이미 APPROVED 상태인 환불
    Order order = RefundFixture.aCompletedOrder();
    Refund approved = RefundFixture.anApprovedRefund(order);
    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(approved));

    // when
    refundService.approveAndReverse(REFUND_ID);

    // then — save / 혜택 정리 호출 없음
    then(refundRepository).should(never()).save(any());
    then(pointService).should(never()).clawback(any());
    then(couponService).should(never()).restore(anyLong());
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 혜택 정리 (reverseBenefits)
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 환불승인시_회수_복원_쿠폰복원_호출() {
    // given: 쿠폰(id=99) + 포인트(500) 적용된 완료 주문
    Order order = RefundFixture.aCompletedOrder();
    ReflectionTestUtils.setField(order, "pointUsed", 500L);
    ReflectionTestUtils.setField(order, "userCouponId", 99L);

    Refund refund = RefundFixture.aRequestedRefund(order);
    RefundResponse response = RefundFixture.aRefundResponse(REFUND_ID);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);
    given(refundMapper.toResponse(refund)).willReturn(response);

    // when
    refundService.approveRefund(SELLER_ID, REFUND_ID);

    // then: clawback 먼저, 그 다음 restore, 그 다음 쿠폰 복원
    then(pointService).should().clawback(order);
    then(pointService).should().restore(eq(order), eq(500L));
    then(couponService).should().restore(99L);
  }

  @Test
  void 환불승인_혜택없는주문_clawback만_호출() {
    // given: 쿠폰·포인트 미사용 주문 (userCouponId=null, pointUsed=null)
    Order order = RefundFixture.aCompletedOrder();
    // pointUsed=null, userCouponId=null (기본값)

    Refund refund = RefundFixture.aRequestedRefund(order);
    RefundResponse response = RefundFixture.aRefundResponse(REFUND_ID);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);
    given(refundMapper.toResponse(refund)).willReturn(response);

    // when
    refundService.approveRefund(SELLER_ID, REFUND_ID);

    // then: clawback 호출됨, restore/couponRestore 는 호출 안 됨
    then(pointService).should().clawback(order);
    then(pointService).should(never()).restore(any(), anyLong());
    then(couponService).should(never()).restore(anyLong());
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 알림 발송
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 환불_승인_시_소비자에게_알림_발송() {
    // given — order.customer.id=1L (OrderFixture.aCustomer())
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.aRequestedRefund(order);
    RefundResponse response = RefundFixture.aRefundResponse(REFUND_ID);

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);
    given(refundMapper.toResponse(refund)).willReturn(response);

    // when
    refundService.approveRefund(SELLER_ID, REFUND_ID);

    // then
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(1L), eq("orderRefund"), eq(NotificationCategory.ORDER), any(), any(), eq("/orders"));
  }

  @Test
  void 자동_환불_승인_시_소비자에게_알림_발송() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.anExpiredRequestedRefund(order);
    given(refundRepository.findById(2L)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);

    // when
    refundService.approveAndReverse(2L);

    // then — customer.id=1L
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(1L), eq("orderRefund"), eq(NotificationCategory.ORDER), any(), any(), eq("/orders"));
  }

  @Test
  void 환불_거부_시_소비자에게_알림_발송() {
    // given
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.aRequestedRefund(order);
    RefundResponse response = RefundFixture.aRefundResponse(REFUND_ID);
    com.magampick.refund.dto.RefundRejectRequest rejectRequest = RefundFixture.aRejectRequest();

    given(refundRepository.findById(REFUND_ID)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);
    given(refundMapper.toResponse(refund)).willReturn(response);

    // when
    refundService.rejectRefund(SELLER_ID, REFUND_ID, rejectRequest);

    // then — customer.id=1L, body=거부사유
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(1L), eq("orderRefund"), eq(NotificationCategory.ORDER), any(), any(), eq("/orders"));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 환불 요청 시 사장 알림
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void 환불_요청_성공_시_사장에게_알림_발송() {
    // given — store.seller.id = 2L (OrderFixture.aSeller())
    Order order = RefundFixture.aCompletedOrder();
    RefundRequestRequest request = RefundFixture.aRefundRequest();
    Refund savedRefund = RefundFixture.aRequestedRefund(order);
    RefundInfoResponse refundInfo = RefundFixture.aRefundInfoResponse();
    OrderResponse base = OrderFixture.anOrderResponse(ORDER_ID);
    OrderResponse withRefund = OrderFixture.anOrderResponse(ORDER_ID);

    given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
    given(refundRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
    given(refundRepository.save(any(Refund.class))).willReturn(savedRefund);
    given(refundMapper.toInfoResponse(savedRefund)).willReturn(refundInfo);
    given(orderMapper.toResponse(order)).willReturn(base);
    given(orderMapper.withRefund(base, refundInfo)).willReturn(withRefund);

    // when
    refundService.requestRefund(CUSTOMER_ID, ORDER_ID, request);

    // then — seller.id=2L 에게 notifySeller 호출
    then(notificationService)
        .should()
        .notifySeller(
            eq(2L),
            eq("refundRequest"),
            eq(NotificationCategory.REFUND),
            any(),
            any(),
            eq("/refunds"));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 리마인드 — findReminderTargets
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void findReminderTargets_D2경과_REQUESTED_reminderSentAt없음_반환() {
    // given — D+2 경과한 REQUESTED + reminderSentAt=null 환불 1건
    Order order = RefundFixture.aCompletedOrder();
    Refund target = RefundFixture.anExpiredRequestedRefund(order);

    given(
            refundRepository.findAllByStatusAndRequestedAtBeforeAndReminderSentAtIsNull(
                any(RefundStatus.class), any(LocalDateTime.class)))
        .willReturn(List.of(target));

    // when
    List<Refund> result = refundService.findReminderTargets();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isSameAs(target);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // 리마인드 — sendReminderAndMark
  // ══════════════════════════════════════════════════════════════════════════

  @Test
  void sendReminderAndMark_발송_후_reminderSentAt_설정됨() {
    // given — REQUESTED + reminderSentAt=null
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.anExpiredRequestedRefund(order);
    given(refundRepository.findById(2L)).willReturn(Optional.of(refund));
    given(refundRepository.save(any(Refund.class))).willReturn(refund);

    // when
    refundService.sendReminderAndMark(2L);

    // then — notifySeller 호출 + reminderSentAt 설정됨 + save 호출
    then(notificationService)
        .should()
        .notifySeller(
            eq(2L),
            eq("refundRequest"),
            eq(NotificationCategory.REFUND),
            any(),
            any(),
            eq("/refunds"));
    assertThat(refund.getReminderSentAt()).isNotNull();
    then(refundRepository).should().save(refund);
  }

  @Test
  void sendReminderAndMark_이미발송됨_skip() {
    // given — reminderSentAt 이 이미 있음
    Order order = RefundFixture.aCompletedOrder();
    Refund refund = RefundFixture.anExpiredRequestedRefund(order);
    ReflectionTestUtils.setField(refund, "reminderSentAt", LocalDateTime.now().minusHours(1));
    given(refundRepository.findById(2L)).willReturn(Optional.of(refund));

    // when
    refundService.sendReminderAndMark(2L);

    // then — notifySeller / save 호출 없음
    then(notificationService).shouldHaveNoInteractions();
    then(refundRepository).should(never()).save(any());
  }

  @Test
  void sendReminderAndMark_REQUESTED아닌상태_skip() {
    // given — 이미 APPROVED 된 환불
    Order order = RefundFixture.aCompletedOrder();
    Refund approved = RefundFixture.anApprovedRefund(order);
    ReflectionTestUtils.setField(approved, "id", 2L);
    given(refundRepository.findById(2L)).willReturn(Optional.of(approved));

    // when
    refundService.sendReminderAndMark(2L);

    // then — notifySeller / save 호출 없음
    then(notificationService).shouldHaveNoInteractions();
    then(refundRepository).should(never()).save(any());
  }
}
