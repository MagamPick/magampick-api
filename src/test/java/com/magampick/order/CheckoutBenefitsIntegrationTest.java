package com.magampick.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.TestcontainersConfiguration;
import com.magampick.coupon.domain.Coupon;
import com.magampick.coupon.domain.CouponDiscountType;
import com.magampick.coupon.domain.CouponKind;
import com.magampick.coupon.domain.CouponStatus;
import com.magampick.coupon.domain.UserCoupon;
import com.magampick.coupon.repository.CouponRepository;
import com.magampick.coupon.repository.UserCouponRepository;
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
import com.magampick.order.repository.OrderRepository;
import com.magampick.order.service.OrderService;
import com.magampick.payment.dto.TossConfirmRequest;
import com.magampick.point.domain.PointAccrual;
import com.magampick.point.domain.PointAccrualStatus;
import com.magampick.point.domain.PointReason;
import com.magampick.point.repository.PointAccrualRepository;
import com.magampick.point.repository.PointTransactionRepository;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.repository.ProductRepository;
import com.magampick.refund.dto.RefundRequestRequest;
import com.magampick.refund.repository.RefundRepository;
import com.magampick.refund.service.RefundService;
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
 * 쿠폰 + 포인트 혜택 적용 주문 체크아웃 통합 테스트. 2단계 플로우: POST /orders → POST /payments/toss/confirm. 메뉴 상품(3500원)
 * + 쿠폰(AMOUNT 2000) + 포인트(500) 적용 시 finalAmount=1000 확인. 결제 후 쿠폰 USED 전환 + 포인트 잔액 차감 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CheckoutBenefitsIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CustomerRepository customerRepository;
  @Autowired SellerRepository sellerRepository;
  @Autowired StoreRepository storeRepository;
  @Autowired StoreBusinessHourRepository storeBusinessHourRepository;
  @Autowired ProductRepository productRepository;
  @Autowired CouponRepository couponRepository;
  @Autowired UserCouponRepository userCouponRepository;
  @Autowired PointAccrualRepository pointAccrualRepository;
  @Autowired PointTransactionRepository pointTransactionRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired OrderService orderService;
  @Autowired RefundService refundService;
  @Autowired RefundRepository refundRepository;
  @Autowired JwtProvider jwtProvider;

  @Test
  void 쿠폰_포인트_적용_주문_전체_흐름() throws Exception {
    // ── given ────────────────────────────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller));
    storeBusinessHourRepository.save(todayBusinessHour(store));

    // 메뉴 상품 (3500원)
    Product menuItem = productRepository.save(newMenuProduct(store));

    // 쿠폰: AMOUNT 2000원, 최소주문 3000원
    Coupon coupon =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label("테스트 2000원 쿠폰")
                .discountType(CouponDiscountType.AMOUNT)
                .discountValue(2000)
                .minOrder(3000)
                .validUntil(LocalDate.now().plusDays(30))
                .active(true)
                .build());

    // 소비자 쿠폰 발급 (USABLE)
    UserCoupon userCoupon =
        userCouponRepository.save(
            UserCoupon.builder()
                .customer(customer)
                .coupon(coupon)
                .status(CouponStatus.USABLE)
                .expiresAt(LocalDate.now().plusDays(30))
                .issuedAt(LocalDateTime.now())
                .build());

    // 포인트 1000P 적립 lot (수동 시드)
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(1000L)
            .remainingAmount(1000L)
            .earnedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // 주문 요청: 쿠폰 + 포인트 500 사용
    CreateOrderRequest request =
        new CreateOrderRequest(
            store.getId(),
            List.of(new OrderItemRequest(ItemKind.MENU, menuItem.getId(), 1)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            userCoupon.getId(),
            500);

    // ── 1단계: 주문 생성 ────────────────────────────────────────────────────────
    // menuSubtotal=3500, couponDiscount=2000, afterCoupon=1500, pointUsed=500, finalAmount=1000
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
    assertThat(amount).isEqualByComparingTo(new BigDecimal("1000")); // 3500 - 2000 - 500

    // ── 2단계: 결제 확인 ────────────────────────────────────────────────────────
    TossConfirmRequest confirmRequest = new TossConfirmRequest("stub_test_paykey", orderId, amount);

    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest)))
        .andExpect(status().isOk());

    // ── 검증 ──────────────────────────────────────────────────────────────────

    // Order.finalAmount = 1000 저장 확인
    var savedOrder = orderRepository.findById(orderId).orElseThrow();
    assertThat(savedOrder.getFinalAmount()).isEqualByComparingTo(new BigDecimal("1000"));

    // 쿠폰 USED 전환 확인
    var refreshedCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
    assertThat(refreshedCoupon.getStatus()).isEqualTo(CouponStatus.USED);

    // 포인트 잔액 500 차감 확인 (1000 → 500)
    long remainingBalance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());
    assertThat(remainingBalance).isEqualTo(500L);

    // USE 포인트 내역 존재 확인 (amount=500)
    var useTxList =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.USE));
    assertThat(useTxList).hasSize(1);
    assertThat(useTxList.get(0).getAmount()).isEqualTo(500L);
  }

  @Test
  void 취소시_쿠폰_포인트_복원됨() throws Exception {
    // ── given ────────────────────────────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller));
    storeBusinessHourRepository.save(todayBusinessHour(store));
    Product menuItem = productRepository.save(newMenuProduct(store));

    Coupon coupon =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label("취소 테스트 2000원 쿠폰")
                .discountType(CouponDiscountType.AMOUNT)
                .discountValue(2000)
                .minOrder(3000)
                .validUntil(LocalDate.now().plusDays(30))
                .active(true)
                .build());

    UserCoupon userCoupon =
        userCouponRepository.save(
            UserCoupon.builder()
                .customer(customer)
                .coupon(coupon)
                .status(CouponStatus.USABLE)
                .expiresAt(LocalDate.now().plusDays(30))
                .issuedAt(LocalDateTime.now())
                .build());

    // 포인트 1000P 시드
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(1000L)
            .remainingAmount(1000L)
            .earnedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // 주문 요청: 쿠폰 + 포인트 500 사용 → finalAmount=1000
    CreateOrderRequest request =
        new CreateOrderRequest(
            store.getId(),
            List.of(new OrderItemRequest(ItemKind.MENU, menuItem.getId(), 1)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            userCoupon.getId(),
            500);

    // ── 1단계: 주문 생성 ────────────────────────────────────────────────────────
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

    // ── 2단계: 결제 확인 (쿠폰 USED, 포인트 500 차감) ──────────────────────────
    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TossConfirmRequest("stub_cancel_restore_key", orderId, amount))))
        .andExpect(status().isOk());

    // ── 3단계: 취소 ──────────────────────────────────────────────────────────
    mockMvc
        .perform(
            post("/api/v1/orders/" + orderId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());

    // ── 검증 ──────────────────────────────────────────────────────────────────

    // 쿠폰 USABLE 복원 확인
    var refreshedCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
    assertThat(refreshedCoupon.getStatus()).isEqualTo(CouponStatus.USABLE);

    // 포인트 잔액 복원 확인 (1000 - 500 + 500 = 1000)
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());
    assertThat(balance).isEqualTo(1000L);

    // RESTORE 포인트 내역 존재 확인
    var restoreTxList =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.RESTORE));
    assertThat(restoreTxList).hasSize(1);
    assertThat(restoreTxList.get(0).getAmount()).isEqualTo(500L);
  }

  @Test
  void 완료시_적립됨() throws Exception {
    // ── given ────────────────────────────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller));
    storeBusinessHourRepository.save(todayBusinessHour(store));
    Product menuItem = productRepository.save(newMenuProduct(store));

    Coupon coupon =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label("완료 테스트 2000원 쿠폰")
                .discountType(CouponDiscountType.AMOUNT)
                .discountValue(2000)
                .minOrder(3000)
                .validUntil(LocalDate.now().plusDays(30))
                .active(true)
                .build());

    UserCoupon userCoupon =
        userCouponRepository.save(
            UserCoupon.builder()
                .customer(customer)
                .coupon(coupon)
                .status(CouponStatus.USABLE)
                .expiresAt(LocalDate.now().plusDays(30))
                .issuedAt(LocalDateTime.now())
                .build());

    // 포인트 1000P 시드
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(1000L)
            .remainingAmount(1000L)
            .earnedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());

    String token = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // 주문 요청: 쿠폰 + 포인트 500 사용 → finalAmount=1000, earnedPoints=10
    CreateOrderRequest request =
        new CreateOrderRequest(
            store.getId(),
            List.of(new OrderItemRequest(ItemKind.MENU, menuItem.getId(), 1)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            userCoupon.getId(),
            500);

    // ── 1단계: 주문 생성 ────────────────────────────────────────────────────────
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

    // ── 2단계: 결제 확인 (PENDING) ─────────────────────────────────────────────
    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TossConfirmRequest("stub_complete_earn_key", orderId, amount))))
        .andExpect(status().isOk());

    // ── 3단계: PENDING → PREPARING → READY → COMPLETED (직접 서비스 호출) ───────
    orderService.acceptOrder(seller.getId(), orderId);
    orderService.readyOrder(seller.getId(), orderId);
    orderService.completeOrder(seller.getId(), orderId);

    // ── 검증 ──────────────────────────────────────────────────────────────────

    // EARN 포인트 내역 확인 (earnedPoints = floor(1000/100) = 10)
    var earnTxList =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.EARN));
    assertThat(earnTxList).hasSize(1);
    assertThat(earnTxList.get(0).getAmount()).isEqualTo(10L);

    // 잔액 증가 확인 (1000 - 500 + 10 = 510)
    long balance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());
    assertThat(balance).isEqualTo(510L);
  }

  @Test
  void 환불승인시_쿠폰포인트_복원_적립회수() throws Exception {
    // ── given ─────────────────────────────────────────────────────────────
    Customer customer = customerRepository.save(newCustomer());
    Seller seller = sellerRepository.save(newSeller());
    Store store = storeRepository.save(newStore(seller));
    storeBusinessHourRepository.save(todayBusinessHour(store));
    Product menuItem = productRepository.save(newMenuProduct(store));

    // 쿠폰 발급 (AMOUNT 1000, 최소주문 2000)
    Coupon coupon =
        couponRepository.save(
            Coupon.builder()
                .kind(CouponKind.EVENT)
                .label("환불테스트 1000원 쿠폰")
                .discountType(CouponDiscountType.AMOUNT)
                .discountValue(1000)
                .minOrder(2000)
                .validUntil(LocalDate.now().plusDays(30))
                .active(true)
                .build());

    UserCoupon userCoupon =
        userCouponRepository.save(
            UserCoupon.builder()
                .customer(customer)
                .coupon(coupon)
                .status(CouponStatus.USABLE)
                .expiresAt(LocalDate.now().plusDays(30))
                .issuedAt(LocalDateTime.now())
                .build());

    // 포인트 2000P 시드
    pointAccrualRepository.save(
        PointAccrual.builder()
            .customer(customer)
            .order(null)
            .initialAmount(2000L)
            .remainingAmount(2000L)
            .earnedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusYears(1))
            .status(PointAccrualStatus.ACTIVE)
            .build());

    String customerToken = jwtProvider.issueAccessToken(customer.getId(), Role.CUSTOMER);

    // ── 1단계: 주문 생성 (쿠폰+포인트 500) ─────────────────────────────────
    // menuSubtotal=3500, coupon=1000, point=500 → finalAmount=2000
    CreateOrderRequest request =
        new CreateOrderRequest(
            store.getId(),
            List.of(new OrderItemRequest(ItemKind.MENU, menuItem.getId(), 1)),
            new PickupRequest(PickupType.ASAP, null),
            null,
            "toss",
            true,
            null,
            userCoupon.getId(),
            500);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode createData =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
    long orderId = createData.path("orderId").asLong();
    BigDecimal amount = new BigDecimal(createData.path("amount").asText());

    // ── 2단계: 결제 확인 (PENDING) ──────────────────────────────────────────
    mockMvc
        .perform(
            post("/api/v1/payments/toss/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new TossConfirmRequest("stub_refund_benefit_key", orderId, amount))))
        .andExpect(status().isOk());

    // ── 3단계: COMPLETED 로 전이 (포인트 적립 발생) ──────────────────────────
    orderService.acceptOrder(seller.getId(), orderId);
    orderService.readyOrder(seller.getId(), orderId);
    orderService.completeOrder(seller.getId(), orderId);

    // 적립 확인
    List<?> earnTxs =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.EARN));
    assertThat(earnTxs).hasSize(1); // EARN lot 1개 생성됨

    // EARN lot의 잔량 확인 (2000 - 500 + earnedPoints)
    long balanceAfterEarn = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());
    assertThat(balanceAfterEarn).isGreaterThan(0);

    // ── 4단계: 환불 요청 ─────────────────────────────────────────────────────
    refundService.requestRefund(
        customer.getId(), orderId, new RefundRequestRequest("상품이 예상과 달랐어요"));

    Long refundId = refundRepository.findByOrderId(orderId).orElseThrow().getId();

    // ── 5단계: 환불 승인 (혜택 정리 트리거) ──────────────────────────────────
    refundService.approveRefund(seller.getId(), refundId);

    // ── 검증 ─────────────────────────────────────────────────────────────────

    // 쿠폰 USABLE 복원 확인
    UserCoupon refreshedCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
    assertThat(refreshedCoupon.getStatus()).isEqualTo(CouponStatus.USABLE);

    // RESTORE 포인트 내역 확인 (사용 500P 복원)
    var restoreTxs =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.RESTORE));
    assertThat(restoreTxs).hasSize(1);
    assertThat(restoreTxs.get(0).getAmount()).isEqualTo(500L);

    // CLAWBACK 내역 확인 (적립분 회수됨)
    var clawbackTxs =
        pointTransactionRepository.findByCustomerIdAndReasonInOrderByOccurredAtDescIdDesc(
            customer.getId(), List.of(PointReason.CLAWBACK));
    assertThat(clawbackTxs).hasSize(1);
    // clawback 금액 > 0 (earnedPoints 만큼 회수됨)
    assertThat(clawbackTxs.get(0).getAmount()).isGreaterThan(0L);

    // 최종 잔액: seed(2000) - use(500) + earn(N) - clawback(N) + restore(500) = 2000
    // EARN과 CLAWBACK이 상쇄되어 원래 2000P 로 돌아옴
    long finalBalance = pointAccrualRepository.sumActiveRemainingByCustomerId(customer.getId());
    assertThat(finalBalance).isEqualTo(2000L);
  }

  // ── helper ────────────────────────────────────────────────────────────────

  private Customer newCustomer() {
    return Customer.builder()
        .email("benefits_" + System.nanoTime() + "@test.com")
        .passwordHash("x")
        .nickname("혜택테스터")
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
        .name("혜택테스트빵집")
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

  private Product newMenuProduct(Store store) {
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
