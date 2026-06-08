package com.magampick.point.service;

import com.magampick.global.exception.BusinessException;
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
import java.util.List;
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
    List<PointAccrual> lots =
        pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
            order.getCustomer().getId(), PointAccrualStatus.ACTIVE);

    long need = amount;
    for (PointAccrual lot : lots) {
      long take = Math.min(lot.getRemainingAmount(), need);
      lot.deduct(take);
      need -= take;
      if (need == 0) {
        break;
      }
    }
    if (need > 0) {
      throw new BusinessException(PointErrorCode.INSUFFICIENT_POINTS);
    }

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

  /** 새 ACTIVE 포인트 lot + 거래 내역 공통 저장. earn/restore 에서 재사용. */
  private void recordAccrual(Order order, long amount, PointReason reason, LocalDateTime now) {
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
