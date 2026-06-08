package com.magampick.point.service;

import com.magampick.point.dto.PointHistoryFilter;
import com.magampick.point.dto.PointSummaryResponse;
import com.magampick.point.dto.PointTransactionResponse;
import com.magampick.point.mapper.PointTransactionMapper;
import com.magampick.point.repository.PointAccrualRepository;
import com.magampick.point.repository.PointTransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포인트 조회 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

  private final PointAccrualRepository pointAccrualRepository;
  private final PointTransactionRepository pointTransactionRepository;
  private final PointTransactionMapper pointTransactionMapper;

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
}
