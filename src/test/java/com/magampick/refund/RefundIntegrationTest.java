package com.magampick.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
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
import com.magampick.refund.domain.RefundStatus;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.repository.RefundRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 환불 요청 → 승인 end-to-end 통합 테스트. 소비자가 COMPLETED 주문에 환불 요청 후 사장이 승인하는 핵심 흐름 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefundIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired RefundRepository refundRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 환불요청_승인_전체흐름() throws Exception {
    // ── given: 테스트 데이터 세팅 ──────────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller));
    storeBusinessHourRepository.save(todayBusinessHour(store));

    ClearanceItem ci = clearanceItemRepository.save(newClearanceItem(store));

    // COMPLETED 주문 직접 생성 (실결제 stub 우회)
    Order order = buildCompletedOrder(customer, store);
    Order savedOrder = orderRepository.save(order);

    // 주문 항목 추가 (RefundResponse items 맵핑에 필요)
    OrderItem item =
        OrderItem.forDeal(
            savedOrder, ci, ci.getName(), ci.getRegularPrice(), null, 1, ci.getSalePrice());
    savedOrder.addOrderItem(item);
    orderRepository.save(savedOrder);

    String customerToken = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);
    String sellerToken = jwtProvider.issueAccessToken(seller.getId(), Role.SELLER);

    // ── when: 소비자 환불 요청 ───────────────────────────────────────────────
    RefundRequestRequest refundRequest = new RefundRequestRequest("상품이 예상과 달랐어요");

    MvcResult refundResult =
        mockMvc
            .perform(
                post("/api/v1/orders/{orderId}/refund", savedOrder.getId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refundRequest)))
            .andExpect(status().isOk())
            .andReturn();

    // ── then: 환불 요청 확인 ──────────────────────────────────────────────────
    JsonNode refundData =
        objectMapper.readTree(refundResult.getResponse().getContentAsString()).path("data");
    assertThat(refundData.path("refund").path("status").asText()).isEqualTo("REQUESTED");
    assertThat(refundData.path("refund").path("reason").asText()).isEqualTo("상품이 예상과 달랐어요");

    // DB에 Refund 저장됐는지 확인
    Optional<Refund> savedRefund = refundRepository.findByOrderId(savedOrder.getId());
    assertThat(savedRefund).isPresent();
    assertThat(savedRefund.get().getStatus()).isEqualTo(RefundStatus.REQUESTED);

    Long refundId = savedRefund.get().getId();

    // ── when: 사장 환불 승인 ─────────────────────────────────────────────────
    MvcResult approveResult =
        mockMvc
            .perform(
                post("/api/v1/seller/refunds/{refundId}/approve", refundId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andReturn();

    // ── then: 승인 결과 확인 ──────────────────────────────────────────────────
    JsonNode approveData =
        objectMapper.readTree(approveResult.getResponse().getContentAsString()).path("data");
    assertThat(approveData.path("status").asText()).isEqualTo("APPROVED");
    assertThat(approveData.path("resolvedAt").isMissingNode()).isFalse();

    // DB 상태 최종 확인
    Refund finalRefund = refundRepository.findById(refundId).orElseThrow();
    assertThat(finalRefund.getStatus()).isEqualTo(RefundStatus.APPROVED);
    assertThat(finalRefund.getResolvedAt()).isNotNull();
  }

  // ── helper ────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("refund_customer_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("환불테스터")
        .build();
  }

  private Seller newSeller() {
    return Seller.builder()
        .email("refund_seller_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .ownerName("환불사장")
        .build();
  }

  private Store newStore(Seller seller) {
    return Store.builder()
        .seller(seller)
        .businessNumber("9876543210")
        .representativeName("환불사장")
        .openDate(LocalDate.of(2020, 1, 1))
        .name("환불테스트빵집")
        .roadAddress("서울시 강남구 테헤란로 99")
        .zonecode("06158")
        .location(GeometryUtil.toPoint(37.5, 127.0))
        .phone("0298765432")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private StoreBusinessHour todayBusinessHour(Store store) {
    DayOfWeek today = LocalDate.now().getDayOfWeek();
    return StoreBusinessHour.builder()
        .store(store)
        .dayOfWeek(today)
        .openTime(LocalTime.of(9, 0))
        .closeTime(LocalTime.of(21, 0))
        .build();
  }

  private ClearanceItem newClearanceItem(Store store) {
    ClearanceItem ci =
        ClearanceItem.builder()
            .store(store)
            .name("환불크로아상")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(10)
            .pickupStartAt(LocalDateTime.now().minusHours(1))
            .pickupEndAt(LocalDateTime.now().plusHours(3))
            .build();
    ReflectionTestUtils.setField(ci, "status", ClearanceItemStatus.CLOSED);
    return ci;
  }

  /** COMPLETED 상태 + completedAt = 어제인 Order 픽스처 (실결제 우회). */
  private Order buildCompletedOrder(Customer customer, Store store) {
    Order order =
        Order.builder()
            .customer(customer)
            .store(store)
            .status(OrderStatus.COMPLETED)
            .totalPrice(new BigDecimal("3000"))
            .pickupType(PickupType.ASAP)
            .pickupCode("1234")
            .normalTotal(new BigDecimal("4500"))
            .discountTotal(new BigDecimal("1500"))
            .build();
    ReflectionTestUtils.setField(order, "completedAt", LocalDateTime.now().minusDays(1));
    return order;
  }
}
