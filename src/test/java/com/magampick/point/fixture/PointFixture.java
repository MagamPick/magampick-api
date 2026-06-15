package com.magampick.point.fixture;

import com.magampick.customer.domain.Customer;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import com.magampick.point.domain.PointReason;
import com.magampick.point.domain.PointTransaction;
import com.magampick.point.dto.PointTransactionResponse;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** 포인트 도메인 테스트 픽스처. */
public class PointFixture {

  private PointFixture() {}

  /** ACTIVE 적립 lot. */
  public static PointAccrual anActiveAccrual(Customer customer, long initialAmount) {
    LocalDateTime now = LocalDateTime.now();
    PointAccrual a =
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(initialAmount)
            .remainingAmount(initialAmount)
            .earnedAt(now)
            .expiresAt(now.plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build();
    ReflectionTestUtils.setField(a, "id", 1L);
    return a;
  }

  /** EXHAUSTED(소진) 적립 lot. */
  public static PointAccrual anExhaustedAccrual(Customer customer, long initialAmount) {
    LocalDateTime now = LocalDateTime.now();
    PointAccrual a =
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(initialAmount)
            .remainingAmount(0L)
            .earnedAt(now.minusDays(10))
            .expiresAt(now.plusYears(1))
            .status(PointAccrualStatus.EXHAUSTED)
            .build();
    ReflectionTestUtils.setField(a, "id", 2L);
    return a;
  }

  /** EARN 포인트 내역. */
  public static PointTransaction anEarnTransaction(Customer customer) {
    PointTransaction tx =
        PointTransaction.builder()
            .customer(customer)
            .order(null)
            .reason(PointReason.EARN)
            .amount(1000L)
            .storeName("동네빵집")
            .occurredAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(tx, "id", 10L);
    return tx;
  }

  /** USE 포인트 내역. */
  public static PointTransaction aUseTransaction(Customer customer) {
    PointTransaction tx =
        PointTransaction.builder()
            .customer(customer)
            .order(null)
            .reason(PointReason.USE)
            .amount(500L)
            .storeName("동네빵집")
            .occurredAt(LocalDateTime.now().minusHours(1))
            .build();
    ReflectionTestUtils.setField(tx, "id", 11L);
    return tx;
  }

  /** EXPIRE 포인트 내역. */
  public static PointTransaction anExpireTransaction(Customer customer) {
    PointTransaction tx =
        PointTransaction.builder()
            .customer(customer)
            .order(null)
            .reason(PointReason.EXPIRE)
            .amount(200L)
            .storeName(null)
            .occurredAt(LocalDateTime.now().minusDays(1))
            .build();
    ReflectionTestUtils.setField(tx, "id", 12L);
    return tx;
  }

  public static PointTransactionResponse aTransactionResponse(Long id, PointReason reason) {
    return new PointTransactionResponse(id, reason, 1000L, "동네빵집", LocalDateTime.now());
  }
}
