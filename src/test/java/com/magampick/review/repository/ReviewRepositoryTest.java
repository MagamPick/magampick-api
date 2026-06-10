package com.magampick.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.repository.OrderRepository;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.repository.ProductRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.fixture.ReviewFixture;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class ReviewRepositoryTest {

  @Autowired ReviewRepository reviewRepository;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired ProductRepository productRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired OrderRepository orderRepository;

  private Customer customer;
  private Store store;
  private ClearanceItem clearanceItem;

  @BeforeEach
  void setUp() {
    reviewRepository.deleteAll();
    orderRepository.deleteAll();
    clearanceItemRepository.deleteAll();

    customer =
        customerRepository.save(
            Customer.builder()
                .email("reviewer_" + System.nanoTime() + "@test.com")
                .passwordHash("hash")
                .nickname("테스터")
                .build());

    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_" + System.nanoTime() + "@test.com")
                .passwordHash("hash")
                .ownerName("사장님")
                .phone("01012345678")
                .build());

    store =
        storeRepository.findAll().stream().findFirst().orElseGet(() -> buildAndSaveStore(seller));

    Product product =
        productRepository.save(
            Product.builder()
                .store(store)
                .name("크로아상")
                .regularPrice(new BigDecimal("4500"))
                .status(ProductStatus.ON_SALE)
                .build());

    clearanceItem =
        clearanceItemRepository.save(
            ClearanceItem.builder()
                .store(store)
                .product(product)
                .name("크로아상")
                .regularPrice(new BigDecimal("4500"))
                .salePrice(new BigDecimal("3000"))
                .totalQuantity(5)
                .pickupStartAt(LocalDate.now().atTime(17, 0))
                .pickupEndAt(LocalDate.now().atTime(21, 0))
                .build());
  }

  // ── 목록 조회 ─────────────────────────────────────────────────────────────────

  @Test
  void 매장_리뷰_목록_최신순_조회() {
    // given — 리뷰 2개, 각각 다른 시각
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));

    Review older =
        reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 3));
    Review newer =
        reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 5));
    // newer 의 createdAt 이 older 보다 커야 함 — flush 후 시각 차이 보장
    // (JPA auditing 은 밀리초 단위이므로 여기선 id 오름차순 = 생성 순서로 검증)

    // when
    Slice<Review> result =
        reviewRepository.findByStoreIdOrderByCreatedAtDesc(store.getId(), PageRequest.of(0, 10));

    // then
    assertThat(result.getContent()).hasSize(2);
    // 최신순 — newer(id 더 큰 쪽)이 먼저
    assertThat(result.getContent().get(0).getId())
        .isGreaterThan(result.getContent().get(1).getId());
  }

  @Test
  void 삭제된_리뷰는_목록에서_제외된다() {
    // given
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));

    Review active = reviewRepository.save(ReviewFixture.aReview(customer, order1, store));
    Review deleted = reviewRepository.save(ReviewFixture.aReview(customer, order2, store));
    // soft-delete: deleted_at 을 직접 주입 (write 미구현이라 delete() 메서드 없음)
    ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
    reviewRepository.save(deleted);

    // when
    Slice<Review> result =
        reviewRepository.findByStoreIdOrderByCreatedAtDesc(store.getId(), PageRequest.of(0, 10));

    // then
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getId()).isEqualTo(active.getId());
  }

  @Test
  void Slice_hasNext_페이지_경계_확인() {
    // given — 리뷰 3개, size=2 로 조회
    for (int i = 0; i < 3; i++) {
      Order order = orderRepository.save(OrderFixture.anOrder(customer, store));
      reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order, store, 3 + i % 3));
    }

    // when
    Slice<Review> page0 =
        reviewRepository.findByStoreIdOrderByCreatedAtDesc(store.getId(), PageRequest.of(0, 2));
    Slice<Review> page1 =
        reviewRepository.findByStoreIdOrderByCreatedAtDesc(store.getId(), PageRequest.of(1, 2));

    // then
    assertThat(page0.getContent()).hasSize(2);
    assertThat(page0.hasNext()).isTrue();
    assertThat(page1.getContent()).hasSize(1);
    assertThat(page1.hasNext()).isFalse();
  }

  @Test
  void 사장_매장_리뷰_목록_List_최신순_삭제제외() {
    // given — active 2개 + soft-delete 1개
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order3 = orderRepository.save(OrderFixture.anOrder(customer, store));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 3));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 5));
    Review deleted =
        reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order3, store, 1));
    ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
    reviewRepository.save(deleted);

    // when
    List<Review> result =
        reviewRepository.findByStoreIdWithCustomerOrderByCreatedAtDesc(store.getId());

    // then — soft-delete 제외, 최신순(id 큰 쪽 먼저)
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isGreaterThan(result.get(1).getId());
  }

  // ── 평점 집계 ─────────────────────────────────────────────────────────────────

  @Test
  void 매장_평균_평점_집계() {
    // given — 별점 4, 5 두 개 → 평균 4.5
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 4));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 5));

    // when
    Object[] stats = reviewRepository.findStoreRatingStats(store.getId()).get(0);

    // then
    assertThat(((Number) stats[0]).doubleValue()).isCloseTo(4.5, within(0.01));
    assertThat(((Number) stats[1]).longValue()).isEqualTo(2L);
  }

  @Test
  void 리뷰_없는_매장_평점_집계_count_0() {
    // when
    Object[] stats = reviewRepository.findStoreRatingStats(store.getId()).get(0);

    // then
    assertThat(stats[0]).isNull(); // AVG of empty set = null
    assertThat(((Number) stats[1]).longValue()).isEqualTo(0L);
  }

  @Test
  void 삭제된_리뷰는_평점_집계에서_제외된다() {
    // given — 별점 5짜리 active, 별점 1짜리 deleted
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 5));
    Review deleted =
        reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 1));
    ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
    reviewRepository.save(deleted);

    // when
    Object[] stats = reviewRepository.findStoreRatingStats(store.getId()).get(0);

    // then
    assertThat(((Number) stats[0]).doubleValue()).isCloseTo(5.0, within(0.01));
    assertThat(((Number) stats[1]).longValue()).isEqualTo(1L);
  }

  @Test
  void 별점_분포_집계() {
    // given — 5점 2개, 4점 1개
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));
    Order order3 = orderRepository.save(OrderFixture.anOrder(customer, store));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 5));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 5));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order3, store, 4));

    // when
    List<Object[]> dist = reviewRepository.findRatingDistribution(store.getId());

    // then — 결과 2행 (5점, 4점만)
    assertThat(dist).hasSize(2);
    // 각 행 [rating, count] 검증
    boolean has5 =
        dist.stream()
            .anyMatch(r -> ((Number) r[0]).intValue() == 5 && ((Number) r[1]).longValue() == 2);
    boolean has4 =
        dist.stream()
            .anyMatch(r -> ((Number) r[0]).intValue() == 4 && ((Number) r[1]).longValue() == 1);
    assertThat(has5).isTrue();
    assertThat(has4).isTrue();
  }

  // ── 떨이 상품 평점 집계 ───────────────────────────────────────────────────────

  @Test
  void 떨이_상품_평점_집계() {
    // given — clearanceItem 을 포함한 주문 → 리뷰 2개 (별점 4, 5)
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    saveOrderItem(order1);
    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));
    saveOrderItem(order2);
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 4));
    reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 5));

    // when
    Object[] stats = reviewRepository.findClearanceItemRatingStats(clearanceItem.getId()).get(0);

    // then
    assertThat(((Number) stats[0]).doubleValue()).isCloseTo(4.5, within(0.01));
    assertThat(((Number) stats[1]).longValue()).isEqualTo(2L);
  }

  @Test
  void 떨이_상품_리뷰_없으면_평점_0건() {
    // when
    Object[] stats = reviewRepository.findClearanceItemRatingStats(clearanceItem.getId()).get(0);

    // then
    assertThat(stats[0]).isNull();
    assertThat(((Number) stats[1]).longValue()).isEqualTo(0L);
  }

  // ── 내부 헬퍼 ────────────────────────────────────────────────────────────────

  private void saveOrderItem(Order order) {
    OrderItem item = OrderFixture.anOrderItem(order, clearanceItem);
    // OrderItem 은 OrderRepository 를 통해 cascade 저장됨 — 직접 저장 필요 시 별도 repository 사용
    // 여기선 @Autowired OrderItemRepository 없이 order.orderItems 에 add + save
    order.getOrderItems().add(item);
    orderRepository.save(order);
  }

  private Store buildAndSaveStore(Seller seller) {
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber(String.valueOf(System.nanoTime()).substring(0, 10))
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name("테스트매장")
            .roadAddress("서울시 강남구 테헤란로 1")
            .zonecode("06158")
            .location(
                new org.locationtech.jts.geom.GeometryFactory()
                    .createPoint(new org.locationtech.jts.geom.Coordinate(127.028, 37.498)))
            .phone("0212345678")
            .operationStatus(com.magampick.store.domain.OperationStatus.OPEN)
            .build());
  }
}
