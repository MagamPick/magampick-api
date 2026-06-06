package com.magampick.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.order.repository.OrderRepository;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.repository.ProductRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.fixture.ReviewFixture;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 주문 → 리뷰 fixture 로 매장 리뷰 목록+평점 end-to-end 검증. FK/조인/트랜잭션을 실제 DB 로 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReviewIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired ProductRepository productRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired ReviewRepository reviewRepository;

  // ── 통합 시나리오 ──────────────────────────────────────────────────────────────

  @Test
  void 주문_리뷰_fixture로_매장_리뷰_목록_end_to_end() throws Exception {
    // given — fixture 구성: 소비자 + 매장 + 떨이 + 주문 + 리뷰
    long ts = System.nanoTime();
    Customer customer =
        customerRepository.save(
            Customer.builder()
                .email("integration_" + ts + "@test.com")
                .passwordHash("hash")
                .nickname("통합테스터")
                .build());

    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_integ_" + ts + "@test.com")
                .passwordHash("hash")
                .businessNumber(String.valueOf(ts).substring(0, 10))
                .ownerName("사장님")
                .phone("01099998888")
                .build());

    Store store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber(String.valueOf(ts).substring(0, 10))
                .name("통합테스트매장")
                .roadAddress("서울시 강남구 테헤란로 427")
                .zonecode("06158")
                .location(new GeometryFactory().createPoint(new Coordinate(127.028, 37.498)))
                .phone("0212345678")
                .operationStatus(OperationStatus.OPEN)
                .build());

    Product product =
        productRepository.save(
            Product.builder()
                .store(store)
                .name("크로아상")
                .regularPrice(new BigDecimal("4500"))
                .status(ProductStatus.ON_SALE)
                .build());

    ClearanceItem item =
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

    // 주문 2개 + 리뷰 2개 (별점 4, 5)
    Order order1 = orderRepository.save(OrderFixture.anOrder(customer, store));
    OrderItem oi1 = OrderFixture.anOrderItem(order1, item);
    order1.getOrderItems().add(oi1);
    orderRepository.save(order1);

    Order order2 = orderRepository.save(OrderFixture.anOrder(customer, store));
    OrderItem oi2 = OrderFixture.anOrderItem(order2, item);
    order2.getOrderItems().add(oi2);
    orderRepository.save(order2);

    Review review1 =
        reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order1, store, 4));
    Review review2 =
        reviewRepository.save(ReviewFixture.aReviewWithRating(customer, order2, store, 5));

    // when — 목록 조회
    MvcResult listResult =
        mockMvc
            .perform(get("/api/v1/stores/{storeId}/reviews", store.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andReturn();

    JsonNode listData =
        objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");

    // then — 목록 2개, 최신순 (review2 가 먼저)
    assertThat(listData.path("content").size()).isEqualTo(2);
    assertThat(listData.path("content").get(0).path("id").asLong())
        .isGreaterThan(listData.path("content").get(1).path("id").asLong());
    assertThat(listData.path("content").get(0).path("authorNickname").asText()).isEqualTo("통합테스터");
    // products 필드 확인 — 떨이 상품 kind="deal"
    assertThat(listData.path("content").get(0).path("products").get(0).path("kind").asText())
        .isEqualTo("deal");

    // when — 요약 조회
    MvcResult summaryResult =
        mockMvc
            .perform(get("/api/v1/stores/{storeId}/reviews/summary", store.getId()))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode summaryData =
        objectMapper.readTree(summaryResult.getResponse().getContentAsString()).path("data");

    // then — 평균 4.5, 건수 2, 분포 5개
    assertThat(summaryData.path("average").asDouble()).isEqualTo(4.5);
    assertThat(summaryData.path("count").asLong()).isEqualTo(2L);
    assertThat(summaryData.path("distribution").size()).isEqualTo(5);
    // 5점→1점 순
    assertThat(summaryData.path("distribution").get(0).path("star").asInt()).isEqualTo(5);
    assertThat(summaryData.path("distribution").get(4).path("star").asInt()).isEqualTo(1);
  }

  @Test
  void 리뷰_없는_매장_목록_빈_결과_및_요약_0점() throws Exception {
    // given — 리뷰 없는 빈 매장
    long ts = System.nanoTime();
    Seller seller =
        sellerRepository.save(
            Seller.builder()
                .email("seller_empty_" + ts + "@test.com")
                .passwordHash("hash")
                .businessNumber(String.valueOf(ts).substring(0, 10))
                .ownerName("사장님2")
                .phone("01088887777")
                .build());

    Store emptyStore =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber(String.valueOf(ts).substring(0, 10))
                .name("빈매장")
                .roadAddress("서울시 강남구 테헤란로 100")
                .zonecode("06158")
                .location(new GeometryFactory().createPoint(new Coordinate(127.029, 37.499)))
                .phone("0212345679")
                .operationStatus(OperationStatus.OPEN)
                .build());

    // when / then — 목록
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews", emptyStore.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.hasNext").value(false));

    // when / then — 요약
    mockMvc
        .perform(get("/api/v1/stores/{storeId}/reviews/summary", emptyStore.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.average").value(0.0))
        .andExpect(jsonPath("$.data.count").value(0));
  }
}
