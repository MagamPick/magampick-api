package com.magampick.point.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import com.magampick.point.domain.PointReason;
import com.magampick.point.domain.PointTransaction;
import com.magampick.point.dto.PointHistoryFilter;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.dto.PointTransactionResponse;
import com.magampick.point.exception.PointErrorCode;
import com.magampick.point.mapper.PointTransactionMapper;
import com.magampick.point.repository.PointAccrualRepository;
import com.magampick.point.repository.PointTransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포인트 조회·적립·사용·복원 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

  private final PointAccrualRepository pointAccrualRepository;
  private final PointTransactionRepository pointTransactionRepository;
  private final PointTransactionMapper pointTransactionMapper;
  private final NotificationService notificationService;
  private final Clock clock;

  /**
   * 소비자 포인트 잔액 조회. ACTIVE lot 잔량 합산.
   *
   * @param customerId 소비자 ID
   * @return 사용 가능 포인트 잔액
   */
  public PointSummaryResponse getSummary(Long customerId) {
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customerId);
    return new PointSummaryResponse(balance);
  }

  /**
   * 소비자 포인트 내역 조회. 필터에 따라 사유 집합을 결정해 조회한다.
   *
   * @param customerId 소비자 ID
   * @param filter ALL / EARN / USE
   * @return 포인트 내역 목록 (최신순)
   */
  public List<PointTransactionResponse> getHistory(Long customerId, PointHistoryFilter filter) {
    return pointTransactionMapper.toResponseList(
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customerId, filter.reasons()));
  }

  /**
   * 주문 완료 시 포인트 적립. amount ≤ 0 이면 무시.
   *
   * <p>새 ACTIVE lot + EARN 내역을 저장한다.
   *
   * @param order 적립 출처 주문
   * @param amount 적립 포인트 (양수여야 함)
   */
  @Transactional
  public void earn(Order order, long amount) {
    if (amount <= 0) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    recordAccrual(order, amount, PointReason.EARN, now);
    log.info("포인트 적립됨. orderId={}, amount={}", order.getId(), amount);
  }

  /**
   * 주문 결제 시 포인트 사용. FIFO 오래된 lot 부터 차감. amount ≤ 0 이면 무시.
   *
   * @param order 사용 주문
   * @param amount 사용 포인트 (양수여야 함)
   * @throws BusinessException INSUFFICIENT_POINTS — 잔액 부족
   */
  @Transactional
  public void use(Order order, long amount) {
    if (amount <= 0) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    // ACTIVE lot 조회
    List<PointAccrual> lots =
        pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
            order.getCustomer().getId(), PointAccrualStatus.ACTIVE);

    // FIFO 차감
    long need = amount;
    for (PointAccrual lot : lots) {
      long take = Math.min(lot.getRemainingAmount(), need);
      lot.deduct(take);
      need -= take;
      if (need == 0) {
        break;
      }
    }
    // 잔액 부족 확인
    if (need > 0) {
      throw new BusinessException(PointErrorCode.INSUFFICIENT_POINTS);
    }

    // 변경 저장
    pointAccrualRepository.saveAll(lots);
    pointTransactionRepository.save(
        PointTransaction.builder()
            .customer(order.getCustomer())
            .order(order)
            .reason(PointReason.USE)
            .amount(amount)
            .storeName(order.getStore().getName())
            .occurredAt(now)
            .build());
    log.info("포인트 사용됨. orderId={}, amount={}", order.getId(), amount);
  }

  /**
   * 취소/환불 시 포인트 복원. 복원 포인트는 새 ACTIVE lot 으로 만료일 1년 재부여. amount ≤ 0 이면 무시.
   *
   * @param order 원주문
   * @param amount 복원 포인트 (양수여야 함)
   */
  @Transactional
  public void restore(Order order, long amount) {
    if (amount <= 0) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    recordAccrual(order, amount, PointReason.RESTORE, now);
    log.info("포인트 복원됨. orderId={}, amount={}", order.getId(), amount);
  }

  /**
   * 픽업 후 환불 시 해당 주문 적립분(EARN lot) 회수. 0 floor — 이미 사용한 잔량은 회수 불가.
   *
   * <p>IMPORTANT: clawback 은 restore 보다 먼저 실행해야 한다. findByOrderId 는 EARN lot 과 (나중에 생성될) RESTORE
   * lot 을 모두 반환하므로, restore 로 새 lot 이 생기기 전에 호출해야 EARN lot 만 대상이 된다.
   *
   * @param order 환불 대상 주문
   */
  @Transactional
  public void clawback(Order order) {
    // 적립 lot 조회
    List<PointAccrual> lots = pointAccrualRepository.findByOrderId(order.getId());
    long reclaimed = 0;
    // 잔여 포인트 차감
    for (PointAccrual lot : lots) {
      long r = lot.getRemainingAmount();
      if (r > 0) {
        lot.deduct(r);
        reclaimed += r;
      }
    }
    // 회수 내역 저장
    if (reclaimed > 0) {
      pointAccrualRepository.saveAll(lots);
      pointTransactionRepository.save(
          PointTransaction.builder()
              .customer(order.getCustomer())
              .order(order)
              .reason(PointReason.CLAWBACK)
              .amount(reclaimed)
              .storeName(order.getStore().getName())
              .occurredAt(LocalDateTime.now(clock))
              .build());
      log.info("포인트 적립 회수됨. orderId={}, amount={}", order.getId(), reclaimed);
    }
  }

  /**
   * 만료 경과 ACTIVE lot 소멸 배치. expiresAt 이 현재 시각보다 이전인 ACTIVE lot 을 EXPIRED 로 전이.
   *
   * @return 처리한 lot 수
   */
  @Transactional
  public int expireAccruals() {
    LocalDateTime now = LocalDateTime.now(clock);
    // 만료 lot 조회
    List<PointAccrual> lots =
        pointAccrualRepository.findByStatusAndExpiresAtBefore(PointAccrualStatus.ACTIVE, now);
    // 소멸 처리
    for (PointAccrual lot : lots) {
      long r = lot.getRemainingAmount();
      lot.expire();
      if (r > 0) {
        pointTransactionRepository.save(
            PointTransaction.builder()
                .customer(lot.getCustomer())
                .order(null)
                .reason(PointReason.EXPIRE)
                .amount(r)
                .storeName(null)
                .occurredAt(now)
                .build());
      }
    }
    // 변경 저장
    if (!lots.isEmpty()) {
      pointAccrualRepository.saveAll(lots);
    }
    log.info("포인트 소멸 배치 완료. 처리 lot 수={}", lots.size());
    return lots.size();
  }

  /**
   * 소멸 30일 전 알림 발송. ACTIVE lot 중 now~now+30일 만료이고 미발송인 lot 을 고객별로 합산해 알림 1건.
   *
   * <p>알림 1건 실패가 다른 고객에게 영향을 주지 않도록 고객 단위로 try-catch.
   */
  @Transactional
  public void notifyExpiringAccruals() {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime thirtyDaysLater = now.plusDays(30);
    // 대상 lot 조회
    List<PointAccrual> lots =
        pointAccrualRepository.findExpiringForAlert(
            PointAccrualStatus.ACTIVE, now, thirtyDaysLater);
    if (lots.isEmpty()) {
      return;
    }

    // 고객별 합산 (LinkedHashMap — 삽입 순서 유지)
    Map<Long, Long> sumByCustomer = new LinkedHashMap<>();
    Map<Long, List<PointAccrual>> lotsByCustomer = new LinkedHashMap<>();
    for (PointAccrual lot : lots) {
      Long cid = lot.getCustomer().getId();
      sumByCustomer.merge(cid, lot.getRemainingAmount(), Long::sum);
      lotsByCustomer.computeIfAbsent(cid, k -> new ArrayList<>()).add(lot);
    }

    // 알림 발송
    for (Map.Entry<Long, Long> entry : sumByCustomer.entrySet()) {
      Long cid = entry.getKey();
      try {
        notificationService.notifyCustomer(
            cid,
            "eventBenefit",
            NotificationCategory.BENEFIT,
            "포인트가 곧 소멸돼요",
            entry.getValue() + "P이 30일 내에 만료됩니다. 지금 사용해보세요!",
            "/points");
        lotsByCustomer.get(cid).forEach(lot -> lot.markExpiryAlertSent(now));
      } catch (Exception e) {
        log.warn("포인트 소멸 예정 알림 발송 실패. customerId={}", cid, e);
      }
    }
  }

  /** 새 ACTIVE 포인트 lot + 거래 내역 공통 저장. earn/restore 에서 재사용. */
  private void recordAccrual(Order order, long amount, PointReason reason, LocalDateTime now) {
    // 포인트 lot 저장
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(order.getCustomer())
            .order(order)
            .initialAmount(amount)
            .remainingAmount(amount)
            .earnedAt(now)
            .expiresAt(now.plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());
    // 거래 내역 저장
    pointTransactionRepository.save(
        PointTransaction.builder()
            .customer(order.getCustomer())
            .order(order)
            .reason(reason)
            .amount(amount)
            .storeName(order.getStore().getName())
            .occurredAt(now)
            .build());
  }
}
