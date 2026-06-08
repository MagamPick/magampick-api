package com.magampick.point.repository;

import com.magampick.point.domain.PointReason;
import com.magampick.point.domain.PointTransaction;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

  /**
   * 소비자 포인트 내역 필터 조회. occurredAt desc, id desc 순 정렬.
   *
   * @param customerId 소비자 ID
   * @param reasons 조회할 사유 목록
   * @return 필터 조건에 맞는 포인트 내역 (최신순)
   */
  List<PointTransaction> findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
      Long customerId, Collection<PointReason> reasons);
}
