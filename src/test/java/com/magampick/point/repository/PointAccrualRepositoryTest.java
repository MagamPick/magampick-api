package com.magampick.point.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.repository.OrderRepository;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * PointAccrualRepository 커스텀 쿼리 검증. findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc — FIFO 정렬
 * findByStatusAndExpiresAtBefore — 소멸 배치 findByOrderId — clawback 배치
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class PointAccrualRepositoryTest {

  @Autowired PointAccrualRepository pointAccrualRepository;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired OrderRepository orderRepository;

  private Customer customer;

  @BeforeEach
  void setUp() {
    customer =
        customerRepository.save(
            Customer.builder()
                .email("accrual_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .nickname("적립테스터")
                .build());
  }

  @Test
  void findByCustomerIdAndStatus_FIFO_정렬() {
    // given: 시간 차이가 있는 ACTIVE lot 3개 (newer 먼저 저장 — id 역순이어도 earnedAt asc 로 정렬돼야 함)
    LocalDateTime oldest = LocalDateTime.now().minusDays(3);
    LocalDateTime middle = LocalDateTime.now().minusDays(2);
    LocalDateTime newest = LocalDateTime.now().minusDays(1);

    saveAccrual(300L, newest);
    saveAccrual(100L, oldest);
    saveAccrual(200L, middle);

    // when
    List<PointAccrual> result =
        pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
            customer.getId(), PointAccrualStatus.ACTIVE);

    // then: FIFO 순 — oldest(100) → middle(200) → newest(300)
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getInitialAmount()).isEqualTo(100L);
    assertThat(result.get(1).getInitialAmount()).isEqualTo(200L);
    assertThat(result.get(2).getInitialAmount()).isEqualTo(300L);
  }

  @Test
  void findByCustomerIdAndStatus_EXHAUSTED_제외() {
    // given: ACTIVE 1개 + EXHAUSTED 1개
    saveAccrual(500L, LocalDateTime.now().minusDays(1));
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(999L)
            .remainingAmount(0L)
            .earnedAt(LocalDateTime.now().minusDays(5))
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.EXHAUSTED)
            .build());

    // when
    List<PointAccrual> result =
        pointAccrualRepository.findByCustomerIdAndStatusOrderByEarnedAtAscIdAsc(
            customer.getId(), PointAccrualStatus.ACTIVE);

    // then: ACTIVE 1개만
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInitialAmount()).isEqualTo(500L);
  }

  // ── findByStatusAndExpiresAtBefore ───────────────────────────────────────────

  @Test
  void findByStatusAndExpiresAtBefore_만료ACTIVE만() {
    // given
    LocalDateTime now = LocalDateTime.now();

    // 만료된 ACTIVE lot (expiresAt = 1일 전)
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(300L)
            .remainingAmount(300L)
            .earnedAt(now.minusYears(2))
            .expiresAt(now.minusDays(1)) // 이미 만료
            .status(PointAccrualStatus.ACTIVE)
            .build());

    // 아직 유효한 ACTIVE lot (expiresAt = 미래)
    saveAccrual(500L, now.minusDays(1));

    // EXHAUSTED lot (만료 여부 무관 — ACTIVE 아님)
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(200L)
            .remainingAmount(0L)
            .earnedAt(now.minusYears(2))
            .expiresAt(now.minusDays(1))
            .status(PointAccrualStatus.EXHAUSTED)
            .build());

    // when
    List<PointAccrual> result =
        pointAccrualRepository.findByStatusAndExpiresAtBefore(PointAccrualStatus.ACTIVE, now);

    // then: 만료된 ACTIVE 1개만 반환 (유효 ACTIVE + EXHAUSTED 제외)
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInitialAmount()).isEqualTo(300L);
  }

  // ── findByOrderId ─────────────────────────────────────────────────────────────

  @Test
  void findByOrderId() {
    // given: 주문에 연결된 lot 1개 + order=null lot 1개
    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_accrual_" + System.nanoTime() + "@test.com")
                .passwordHash("x")
                .ownerName("사장님")
                .build());
    Store store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber("1234567890")
                .representativeName("사장님")
                .openDate(LocalDate.of(2020, 1, 1))
                .name("테스트빵집")
                .roadAddress("서울 강남구 테헤란로 1")
                .zonecode("06158")
                .location(GeometryUtil.toPoint(37.5, 127.0))
                .phone("0212345678")
                .operationStatus(OperationStatus.OPEN)
                .build());
    Order order =
        orderRepository.save(
            Order.builder()
                .customer(customer)
                .store(store)
                .status(OrderStatus.COMPLETED)
                .totalPrice(new BigDecimal("3000"))
                .pickupType(PickupType.ASAP)
                .pickupCode("1234")
                .normalTotal(new BigDecimal("3000"))
                .discountTotal(BigDecimal.ZERO)
                .build());

    // 주문 연결 lot
    LocalDateTime now = LocalDateTime.now();
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(order)
            .initialAmount(1000L)
            .remainingAmount(1000L)
            .earnedAt(now.minusDays(1))
            .expiresAt(now.plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());

    // order=null lot (다른 lot)
    saveAccrual(500L, now.minusDays(2));

    // when
    List<PointAccrual> result = pointAccrualRepository.findByOrderId(order.getId());

    // then: 주문 연결 lot 1개만 반환
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInitialAmount()).isEqualTo(1000L);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private void saveAccrual(long amount, LocalDateTime earnedAt) {
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(amount)
            .remainingAmount(amount)
            .earnedAt(earnedAt)
            .expiresAt(earnedAt.plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());
  }
}
