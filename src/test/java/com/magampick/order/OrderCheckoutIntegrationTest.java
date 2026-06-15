package com.magampick.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.PickupType;
import com.magampick.order.dto.CreateOrderRequest;
import com.magampick.order.dto.CreateOrderRequest.OrderItemRequest;
import com.magampick.order.dto.CreateOrderRequest.PickupRequest;
import com.magampick.payment.domain.Payment;
import com.magampick.payment.domain.PaymentStatus;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.payment.repository.PaymentRepository;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.repository.ProductRepository;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성 + 결제 end-to-end 통합 테스트. 2단계 플로우: POST /orders (AWAITING_PAYMENT) → POST
 * /payments/toss/confirm (PENDING). 떨이 + 일반 상품 혼합 주문: 재고차감 + Payment APPROVED + 픽업코드 4자리 전 흐름 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderCheckoutIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired ClearanceItemRepository clearanceItemRepository;
  @Autowired ProductRepository productRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 떨이_일반_혼합_주문_전체_흐름() throws Exception {
    // ── given ────────────────────────────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller));
    storeBusinessHourRepository.save(todayBusinessHour(store));

    ClearanceItem dealItem = clearanceItemRepository.save(newClearanceItem(store, 10));
    Product menuItem = productRepository.save(newProduct(store));
    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    CreateOrderRequest request =
        new CreateOrderRequest(
            store.getId(),
            List.of(
                new OrderItemRequest(ItemKind.DEAL, dealItem.getId(), 2),
                new OrderItemRequest(ItemKind.MENU, menuItem.getId(), 1)),
            new PickupRequest(PickupType.ASAP, null),
            "빵 나오면 바로 주세요",
            "toss",
            true,
            null,
            null,
            null);

    // ── 1단계: 주문 생성 → AWAITING_PAYMENT ────────────────────────────────────
    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode createData =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
    long orderId = createData.path("orderId").asLong();
    BigDecimal amount = new BigDecimal(createData.path("amount").asText());

    assertThat(orderId).isPositive();
    assertThat(createData.path("tossOrderId").asText()).isEqualTo("order-" + orderId);
    assertThat(amount).isGreaterThan(BigDecimal.ZERO);

    // 재고 차감은 주문 생성 시점에 완료
    ClearanceItem refreshedDeal = clearanceItemRepository.findById(dealItem.getId()).orElseThrow();
    assertThat(refreshedDeal.getRemainingQuantity()).isEqualTo(8); // 10 - 2 = 8

    // ── 2단계: 결제 확인 → PENDING ────────────────────────────────────────────
    TossConfirmRequest confirmRequest = new TossConfirmRequest("stub_test_paykey", orderId, amount);

    MvcResult confirmResult =
        mockMvc
            .perform(
                post("/api/v1/payments/toss/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(confirmRequest)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode confirmData =
        objectMapper.readTree(confirmResult.getResponse().getContentAsString()).path("data");
    assertThat(confirmData.path("status").asText()).isEqualTo("PENDING");
    assertThat(confirmData.path("pickupCode").asText()).matches("\\d{4}");
    assertThat(confirmData.path("paymentMethod").asText()).isEqualTo("toss");
    assertThat(confirmData.path("items").size()).isEqualTo(2);
    assertThat(confirmData.path("amounts").path("payTotal").asLong()).isGreaterThan(0L);

    // Payment 저장 확인
    Optional<Payment> paymentOpt =
        paymentRepository.findAll().stream()
            .filter(p -> p.getOrder().getId().equals(orderId))
            .findFirst();
    assertThat(paymentOpt).isPresent();
    Payment payment = paymentOpt.get();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    assertThat(payment.getPaymentKey()).startsWith("stub_");
  }

  @Test
  void 미인증_401() throws Exception {
    CreateOrderRequest request =
        new CreateOrderRequest(
            1L,
            List.of(new OrderItemRequest(ItemKind.DEAL, 100L, 1)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  // ── helper ────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("customer_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("테스트고객")
        .build();
  }

  private Seller newSeller() {
    return Seller.builder()
        .email("seller_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .ownerName("테스트사장")
        .build();
  }

  private Store newStore(Seller seller) {
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .representativeName("테스트사장")
        .openDate(LocalDate.of(2020, 1, 1))
        .name("동네빵집")
        .roadAddress("서울시 강남구 테헤란로 1")
        .zonecode("06158")
        .location(GeometryUtil.toPoint(37.5, 127.0))
        .phone("0212345678")
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

  private ClearanceItem newClearanceItem(Store store, int quantity) {
    return ClearanceItem.builder()
        .store(store)
        .name("크로아상")
        .regularPrice(new BigDecimal("4500"))
        .salePrice(new BigDecimal("3000"))
        .totalQuantity(quantity)
        .pickupStartAt(LocalDateTime.now().minusHours(1))
        .pickupEndAt(LocalDateTime.now().plusHours(3))
        .build();
  }

  private Product newProduct(Store store) {
    return Product.builder()
        .store(store)
        .name("아메리카노")
        .regularPrice(new BigDecimal("3500"))
        .imageUrl("/uploads/americano.jpg")
        .status(ProductStatus.ON_SALE)
        .category(ProductCategory.BEVERAGE)
        .build();
  }
}
