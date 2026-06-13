package com.magampick.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.domain.PickupType;
import com.magampick.order.repository.OrderRepository;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.repository.RefundRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewReply;
import com.magampick.review.domain.ReviewTag;
import com.magampick.review.repository.ReviewReplyRepository;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 집계 정확성 end-to-end 검증. 완료주문·떨이·환불승인·리뷰를 실제 DB 에 시드 후 GET analytics 호출. 환불승인 주문이 매출·떨이에서 빠지는지 핵심
 * 검증 포함.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AnalyticsIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired EntityManager entityManager;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired RefundRepository refundRepository;
  @Autowired ReviewRepository reviewRepository;
  @Autowired ReviewReplyRepository reviewReplyRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 오늘_통계_집계_정확성_end_to_end() throws Exception {
    // ── given: 기본 엔티티 ──────────────────────────────────────────────────────
    long ts = System.nanoTime();
    Customer customer = customerRepository.save(newCustomer(ts));
    Seller seller = sellerRepository.save(newSeller(ts));
    Store store = storeRepository.save(newStore(seller, ts));
    ClearanceItem ci = clearanceItemRepository.save(newClearanceItem(store));

    LocalDateTime now = LocalDateTime.now();

    // ── 완료 주문 1 (환불 없음): totalPrice=10000, DEAL qty=2, orig=6000, sale=4000 ──
    Order order1 = buildAndComplete(customer, store, new BigDecimal("10000"), now);
    Order savedOrder1 = orderRepository.save(order1);
    OrderItem item1 =
        OrderItem.forDeal(
            savedOrder1, ci, "크로아상", new BigDecimal("6000"), null, 2, new BigDecimal("4000"));
    savedOrder1.addOrderItem(item1);
    orderRepository.save(savedOrder1);

    // ── 완료 주문 2 (환불 없음): totalPrice=15000, DEAL qty=1, orig=8000, sale=5000 ──
    Order order2 = buildAndComplete(customer, store, new BigDecimal("15000"), now);
    Order savedOrder2 = orderRepository.save(order2);
    OrderItem item2 =
        OrderItem.forDeal(
            savedOrder2, ci, "크로아상", new BigDecimal("8000"), null, 1, new BigDecimal("5000"));
    savedOrder2.addOrderItem(item2);
    orderRepository.save(savedOrder2);

    // ── 완료 주문 3 (환불승인): totalPrice=20000, DEAL qty=1 → 매출·떨이에서 제외돼야 함 ──
    Order order3 = buildAndComplete(customer, store, new BigDecimal("20000"), now);
    Order savedOrder3 = orderRepository.save(order3);
    OrderItem item3 =
        OrderItem.forDeal(
            savedOrder3, ci, "크로아상", new BigDecimal("5000"), null, 1, new BigDecimal("3000"));
    savedOrder3.addOrderItem(item3);
    orderRepository.save(savedOrder3);

    // 환불 승인
    Refund refund = Refund.builder().order(savedOrder3).reason("환불 요청").requestedAt(now).build();
    refund.approve(now);
    refundRepository.save(refund);

    // ── 기타 상태 주문 (created_at = now — 오늘 범위) ──────────────────────────────
    Order cancelledOrder = buildOrder(customer, store, OrderStatus.PENDING, new BigDecimal("5000"));
    cancelledOrder.cancel(now);
    orderRepository.save(cancelledOrder);

    Order rejectedOrder = buildOrder(customer, store, OrderStatus.PENDING, new BigDecimal("5000"));
    rejectedOrder.reject(now);
    orderRepository.save(rejectedOrder);

    Order noShowOrder = buildOrder(customer, store, OrderStatus.READY, new BigDecimal("5000"));
    noShowOrder.noShow();
    orderRepository.save(noShowOrder);

    Order pendingOrder = buildOrder(customer, store, OrderStatus.PENDING, new BigDecimal("5000"));
    orderRepository.save(pendingOrder);

    // AWAITING_PAYMENT — 총 주문 수에서 제외돼야 함
    Order awaitingOrder =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.AWAITING_PAYMENT)
            .totalPrice(new BigDecimal("5000"))
            .pickupType(PickupType.ASAP)
            .pickupCode("9999")
            .build();
    orderRepository.save(awaitingOrder);

    // ── 리뷰 2건 (오늘) ────────────────────────────────────────────────────────
    Review review1 =
        Review.builder()
            .customer(customer)
            .order(savedOrder1)
            .store(store)
            .rating(5)
            .content("너무 맛있어요!")
            .build();
    review1.update(5, "너무 맛있어요!", java.util.Set.of(ReviewTag.DELICIOUS));
    Review savedReview1 = reviewRepository.save(review1);

    Review review2 =
        Review.builder()
            .customer(customer)
            .order(savedOrder2)
            .store(store)
            .rating(4)
            .content("신선해요")
            .build();
    review2.update(4, "신선해요", java.util.Set.of(ReviewTag.FRESH));
    reviewRepository.save(review2);

    // 리뷰1에만 답글
    ReviewReply reply =
        ReviewReply.builder().review(savedReview1).seller(seller).content("감사합니다!").build();
    reviewReplyRepository.save(reply);

    // ── when — 세션 캐시 비우기: JPA 1차 캐시 stale 방지 (reviewReply 역방향 캐시 포함) ──────────
    entityManager.flush();
    entityManager.clear();
    Long storeId = store.getId();
    Long sellerId = seller.getId();
    String sellerToken = jwtProvider.issueAccessToken(sellerId, Role.SELLER);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/seller/stores/{storeId}/analytics", storeId)
                    .param("period", "today")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andReturn();

    // ── then ────────────────────────────────────────────────────────────────────
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

    // 매출: order1(10000) + order2(15000) = 25000. order3(환불승인)은 제외
    assertThat(data.path("sales").path("totalSales").asLong()).isEqualTo(25000L);

    // 주문: total=6(COMPLETED2+CANCELLED1+REJECTED1+NO_SHOW1+PENDING1), AWAITING_PAYMENT·환불승인(order3)
    // 제외
    assertThat(data.path("orders").path("total").asInt()).isEqualTo(6);
    assertThat(data.path("orders").path("pickedUp").asInt())
        .isEqualTo(2); // COMPLETED 2건 (order3 환불승인 제외 — 매출 모집단과 일치)
    assertThat(data.path("orders").path("canceled").asInt()).isEqualTo(2); // CANCELLED+REJECTED
    assertThat(data.path("orders").path("noShow").asInt()).isEqualTo(1);

    // 떨이: order1(qty=2)+order2(qty=1)=3. order3(환불승인) 제외
    assertThat(data.path("clearance").path("soldQty").asInt()).isEqualTo(3);
    // savedAmount = (6000-4000)*2 + (8000-5000)*1 = 4000 + 3000 = 7000
    assertThat(data.path("clearance").path("savedAmount").asLong()).isEqualTo(7000L);
    // avgDiscountRate = round(7000 / (6000*2 + 8000*1) * 100) = round(7000/20000*100) = 35
    assertThat(data.path("clearance").path("avgDiscountRate").asInt()).isEqualTo(35);

    // 리뷰: 2건, avgRating=4.5, replyRate=50(1/2건)
    assertThat(data.path("review").path("newCount").asInt()).isEqualTo(2);
    assertThat(data.path("review").path("avgRating").asDouble()).isEqualTo(4.5);
    assertThat(data.path("review").path("replyRate").asInt()).isEqualTo(50);
    // 태그 7종 전부 포함
    assertThat(data.path("review").path("tags").size()).isEqualTo(7);
    // DELICIOUS(1) 또는 FRESH(1) 이 최상위 (동률이면 enum 선언순: DELICIOUS=0)
    assertThat(data.path("review").path("tags").get(0).path("tag").asText())
        .isEqualTo(ReviewTag.DELICIOUS.getLabel());
  }

  @Test
  void 환불승인_주문_매출_제외_확인() throws Exception {
    // given — COMPLETED 주문 1건 (환불승인 있음) + COMPLETED 주문 1건 (환불승인 없음)
    long ts = System.nanoTime() + 1;
    Customer customer = customerRepository.save(newCustomer(ts));
    Seller seller = sellerRepository.save(newSeller(ts));
    Store store = storeRepository.save(newStore(seller, ts));

    LocalDateTime now = LocalDateTime.now();

    Order orderNoRefund = buildAndComplete(customer, store, new BigDecimal("10000"), now);
    orderRepository.save(orderNoRefund);

    Order orderWithRefund = buildAndComplete(customer, store, new BigDecimal("20000"), now);
    Order savedRefundOrder = orderRepository.save(orderWithRefund);
    Refund refund = Refund.builder().order(savedRefundOrder).reason("테스트").requestedAt(now).build();
    refund.approve(now);
    refundRepository.save(refund);

    // when — 세션 캐시 비우기: JPA 1차 캐시 stale 방지
    entityManager.flush();
    entityManager.clear();
    Long storeId = store.getId();
    Long sellerId = seller.getId();
    String sellerToken = jwtProvider.issueAccessToken(sellerId, Role.SELLER);
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/seller/stores/{storeId}/analytics", storeId)
                    .param("period", "today")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andReturn();

    // then — 환불승인 주문(20000)은 제외, 정상 주문(10000)만 집계
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.path("sales").path("totalSales").asLong()).isEqualTo(10000L);
  }

  // ── helper ────────────────────────────────────────────────────────────────────

  private Customer newCustomer(long ts) {
    return Customer.builder()
        .email("analytics_c_" + ts + "@test.com")
        .passwordHash("x")
        .nickname("통합테스터")
        .build();
  }

  private Seller newSeller(long ts) {
    return Seller.builder()
        .email("analytics_s_" + ts + "@test.com")
        .passwordHash("x")
        .ownerName("통계사장")
        .build();
  }

  private Store newStore(Seller seller, long ts) {
    return Store.builder()
        .seller(seller)
        .businessNumber(String.format("%010d", Math.abs(ts) % 10000000000L))
        .representativeName("통계사장")
        .name("통계테스트빵집_" + ts)
        .roadAddress("서울시 강남구 테헤란로 1")
        .zonecode("06158")
        .openDate(LocalDate.of(2024, 1, 1))
        .location(GeometryUtil.toPoint(37.5, 127.0))
        .phone("0212345678")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private ClearanceItem newClearanceItem(Store store) {
    return ClearanceItem.builder()
        .store(store)
        .name("크로아상")
        .regularPrice(new BigDecimal("6000"))
        .salePrice(new BigDecimal("4000"))
        .totalQuantity(20)
        .pickupStartAt(LocalDateTime.now().minusHours(1))
        .pickupEndAt(LocalDateTime.now().plusHours(3))
        .build();
  }

  /** COMPLETED 상태 주문 빌드. complete() 로 completedAt 설정. */
  private Order buildAndComplete(
      Customer customer, Store store, BigDecimal totalPrice, LocalDateTime completedAt) {
    Order o =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.PENDING)
            .totalPrice(totalPrice)
            .pickupType(PickupType.ASAP)
            .pickupCode("1234")
            .build();
    o.complete(completedAt);
    return o;
  }

  /** 지정 상태 주문 빌드. */
  private Order buildOrder(
      Customer customer, Store store, OrderStatus initialStatus, BigDecimal totalPrice) {
    return Order.builder()
        .customer(customer)
        .store(store)
        .status(initialStatus)
        .totalPrice(totalPrice)
        .pickupType(PickupType.ASAP)
        .pickupCode("1234")
        .build();
  }
}
