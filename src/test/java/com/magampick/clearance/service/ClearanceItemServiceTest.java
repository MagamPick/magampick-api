package com.magampick.clearance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.ClearanceItemCreateRequest;
import com.magampick.clearance.dto.ClearanceItemResponse;
import com.magampick.clearance.dto.ClearanceItemUpdateRequest;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.fixture.ClearanceItemFixture;
import com.magampick.clearance.mapper.ClearanceItemMapper;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.service.StoreService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClearanceItemServiceTest {

  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock ProductRepository productRepository;
  @Mock StoreService storeService;
  @Mock ClearanceItemMapper clearanceItemMapper;
  @Mock ClearanceNotificationService clearanceNotificationService;
  @InjectMocks ClearanceItemService clearanceItemService;

  private static final Long SELLER_ID = 1L;
  private static final Long STORE_ID = 10L;
  private static final Long PRODUCT_ID = 100L;
  private static final Long CLEARANCE_ITEM_ID = 200L;
  // ClearanceItemService 가 픽업 시각을 KST 기준으로 검증 → 테스트도 동일 기준이어야 자정 직후 CI 가 실패 안 한다
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private Seller seller() {
    Seller s =
        Seller.builder().email("seller@test.com").passwordHash("hash").ownerName("홍길동").build();
    ReflectionTestUtils.setField(s, "id", SELLER_ID);
    return s;
  }

  private Store store() {
    Store s =
        Store.builder()
            .seller(seller())
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .imageUrl("/uploads/store.jpg")
            .build();
    ReflectionTestUtils.setField(s, "id", STORE_ID);
    return s;
  }

  private Product product(ProductStatus productStatus) {
    Store s = store();
    Product p =
        Product.builder()
            .store(s)
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .imageUrl("/uploads/2026/5/product.jpg")
            .status(productStatus)
            .build();
    ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
    return p;
  }

  private LocalDateTime todayAt(int hour, int minute) {
    return LocalDate.now(KST).atTime(hour, minute);
  }

  // ── 등록 ─────────────────────────────────────────────────────────────────────

  @Test
  void 마감_임박_상품_등록_성공() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItemCreateRequest request = ClearanceItemFixture.aCreateRequest(PRODUCT_ID);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(false);
    given(clearanceItemRepository.saveAndFlush(any(ClearanceItem.class)))
        .willAnswer(
            inv -> {
              ClearanceItem item = inv.getArgument(0);
              ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
              return item;
            });
    given(clearanceItemMapper.toResponse(any(ClearanceItem.class)))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    ClearanceItemResponse response =
        clearanceItemService.registerClearanceItem(SELLER_ID, STORE_ID, request);

    // then
    assertThat(response.id()).isEqualTo(CLEARANCE_ITEM_ID);
    assertThat(response.status()).isEqualTo(ClearanceItemStatus.OPEN);
    then(clearanceItemRepository).should().saveAndFlush(any(ClearanceItem.class));
  }

  @Test
  void 떨이_등록_성공_시_알림_발송_호출됨() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItemCreateRequest request = ClearanceItemFixture.aCreateRequest(PRODUCT_ID);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(false);
    given(clearanceItemRepository.saveAndFlush(any(ClearanceItem.class)))
        .willAnswer(
            inv -> {
              ClearanceItem item = inv.getArgument(0);
              ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
              return item;
            });
    given(clearanceItemMapper.toResponse(any(ClearanceItem.class)))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    clearanceItemService.registerClearanceItem(SELLER_ID, STORE_ID, request);

    // then — 알림 서비스 호출 확인
    then(clearanceNotificationService).should().notifyNewClearanceItem(any(ClearanceItem.class));
  }

  @Test
  void 마감_임박_상품_등록_타인_매장_접근_거부() {
    // given
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.registerClearanceItem(
                    SELLER_ID, STORE_ID, ClearanceItemFixture.aCreateRequest(PRODUCT_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_원본_상품_없음_예외() {
    // given
    Store store = store();
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.registerClearanceItem(
                    SELLER_ID, STORE_ID, ClearanceItemFixture.aCreateRequest(PRODUCT_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_원본_상품_품절_예외() {
    // given
    Store store = store();
    Product soldOut = product(ProductStatus.SOLD_OUT);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(soldOut));

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.registerClearanceItem(
                    SELLER_ID, STORE_ID, ClearanceItemFixture.aCreateRequest(PRODUCT_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_PRODUCT_NOT_ON_SALE);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_이미_진행중인_마감_임박_상품_존재_예외() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(true);

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.registerClearanceItem(
                    SELLER_ID, STORE_ID, ClearanceItemFixture.aCreateRequest(PRODUCT_ID)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_OPEN_EXISTS);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_판매가_정상가_이상_예외() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    // salePrice == regularPrice (4500)
    ClearanceItemCreateRequest request =
        new ClearanceItemCreateRequest(PRODUCT_ID, new BigDecimal("4500"), 5, todayAt(23, 59));
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(false);

    // when / then
    assertThatThrownBy(
            () -> clearanceItemService.registerClearanceItem(SELLER_ID, STORE_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_픽업_시간_당일_초과_예외() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    LocalDateTime tomorrow = LocalDate.now(KST).plusDays(1).atTime(21, 0);
    ClearanceItemCreateRequest request =
        new ClearanceItemCreateRequest(PRODUCT_ID, new BigDecimal("3000"), 5, tomorrow);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(false);

    // when / then
    assertThatThrownBy(
            () -> clearanceItemService.registerClearanceItem(SELLER_ID, STORE_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_INVALID_PICKUP_WINDOW);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_픽업종료_과거_예외() {
    // given — pickupEndAt 이 과거(1시간 전)이면 INVALID_PICKUP_WINDOW
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    // 1시간 전: 오늘이면 isAfter(now) 실패 → 어제면 equals(today) 실패 → 둘 다 예외
    LocalDateTime pastEndAt = LocalDateTime.now(KST).minusHours(1);
    ClearanceItemCreateRequest request =
        new ClearanceItemCreateRequest(PRODUCT_ID, new BigDecimal("3000"), 5, pastEndAt);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(false);

    // when / then
    assertThatThrownBy(
            () -> clearanceItemService.registerClearanceItem(SELLER_ID, STORE_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_INVALID_PICKUP_WINDOW);
    then(clearanceItemRepository).should(never()).save(any());
  }

  @Test
  void 마감_임박_상품_등록_pickupStartAt_서버_now로_자동_설정() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItemCreateRequest request = ClearanceItemFixture.aCreateRequest(PRODUCT_ID);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(prod));
    given(clearanceItemRepository.existsByProductIdAndStatus(PRODUCT_ID, ClearanceItemStatus.OPEN))
        .willReturn(false);
    LocalDateTime beforeCall = LocalDateTime.now(KST);
    given(clearanceItemRepository.saveAndFlush(any(ClearanceItem.class)))
        .willAnswer(
            inv -> {
              ClearanceItem item = inv.getArgument(0);
              ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
              // pickupStartAt 은 요청과 무관하게 서버 now 로 설정됨
              assertThat(item.getPickupStartAt()).isAfterOrEqualTo(beforeCall);
              return item;
            });
    given(clearanceItemMapper.toResponse(any(ClearanceItem.class)))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    clearanceItemService.registerClearanceItem(SELLER_ID, STORE_ID, request);

    // then — saveAndFlush 안의 assertion 으로 검증
    then(clearanceItemRepository).should().saveAndFlush(any(ClearanceItem.class));
  }

  // ── 조회 ─────────────────────────────────────────────────────────────────────

  @Test
  void 본인_매장_마감_임박_상품_목록_조회_성공() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    Pageable pageable = PageRequest.of(0, 20);
    Page<ClearanceItem> page = new PageImpl<>(List.of(item), pageable, 1);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByStoreId(STORE_ID, pageable)).willReturn(page);
    given(clearanceItemMapper.toResponse(item))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    PageResponse<ClearanceItemResponse> result =
        clearanceItemService.getMyClearanceItems(SELLER_ID, STORE_ID, pageable);

    // then
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).id()).isEqualTo(CLEARANCE_ITEM_ID);
    assertThat(result.totalCount()).isEqualTo(1);
  }

  @Test
  void 본인_매장_마감_임박_상품_목록_조회_타인_매장_접근_거부() {
    // given
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.getMyClearanceItems(
                    SELLER_ID, STORE_ID, PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
    then(clearanceItemRepository).should(never()).findByStoreId(any(), any());
  }

  @Test
  void 본인_매장_마감_임박_상품_상세_조회_성공() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));
    given(clearanceItemMapper.toResponse(item))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    ClearanceItemResponse response =
        clearanceItemService.getMyClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID);

    // then
    assertThat(response.id()).isEqualTo(CLEARANCE_ITEM_ID);
  }

  @Test
  void 본인_매장_마감_임박_상품_상세_조회_없음_예외() {
    // given
    Store store = store();
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> clearanceItemService.getMyClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND);
  }

  // ── 수정 ─────────────────────────────────────────────────────────────────────

  @Test
  void 마감_임박_상품_수정_성공() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    ClearanceItemUpdateRequest request =
        new ClearanceItemUpdateRequest(new BigDecimal("2000"), null, null);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));
    given(clearanceItemMapper.toResponse(item))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    ClearanceItemResponse response =
        clearanceItemService.updateClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID, request);

    // then
    assertThat(response.id()).isEqualTo(CLEARANCE_ITEM_ID);
    assertThat(item.getSalePrice()).isEqualByComparingTo(new BigDecimal("2000"));
  }

  @Test
  void 마감_임박_상품_수정_남은수량_변경_시_판매분_보존() {
    // given — 등록 5개 중 2개 판매되어 남은 3개 (sold = 5 - 3 = 2)
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    ReflectionTestUtils.setField(item, "remainingQuantity", 3);
    // 사장이 남은 수량을 4로 수정
    ClearanceItemUpdateRequest request = new ClearanceItemUpdateRequest(null, 4, null);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));
    given(clearanceItemMapper.toResponse(item))
        .willReturn(ClearanceItemFixture.aResponse(CLEARANCE_ITEM_ID));

    // when
    clearanceItemService.updateClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID, request);

    // then — 남은 수량 = 요청값(4), 등록 수량 = 판매분(2) + 요청값(4) = 6, 판매분 보존
    assertThat(item.getRemainingQuantity()).isEqualTo(4);
    assertThat(item.getTotalQuantity()).isEqualTo(6);
    assertThat(item.getStatus()).isEqualTo(ClearanceItemStatus.OPEN);
  }

  @Test
  void 마감_임박_상품_수정_CLOSED_상태_예외() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    item.close();
    ClearanceItemUpdateRequest request =
        new ClearanceItemUpdateRequest(new BigDecimal("2000"), null, null);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.updateClearanceItem(
                    SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_OPEN);
  }

  @Test
  void 마감_임박_상품_수정_판매가_정상가_이상_예외() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE); // regularPrice = 4500
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    ClearanceItemUpdateRequest request =
        new ClearanceItemUpdateRequest(new BigDecimal("5000"), null, null);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.updateClearanceItem(
                    SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED);
  }

  @Test
  void 마감_임박_상품_수정_픽업창_내일_지정_예외() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    LocalDateTime tomorrow = LocalDate.now(KST).plusDays(1).atTime(21, 0);
    ClearanceItemUpdateRequest request = new ClearanceItemUpdateRequest(null, null, tomorrow);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.updateClearanceItem(
                    SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_INVALID_PICKUP_WINDOW);
  }

  @Test
  void 마감_임박_상품_수정_없음_예외() {
    // given
    Store store = store();
    ClearanceItemUpdateRequest request =
        new ClearanceItemUpdateRequest(new BigDecimal("2000"), null, null);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                clearanceItemService.updateClearanceItem(
                    SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND);
  }

  // ── 수동 마감 ─────────────────────────────────────────────────────────────────

  @Test
  void 마감_임박_상품_수동_마감_성공() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));
    given(clearanceItemMapper.toResponse(item))
        .willReturn(ClearanceItemFixture.aClosedResponse(CLEARANCE_ITEM_ID));

    // when
    ClearanceItemResponse response =
        clearanceItemService.closeClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID);

    // then
    assertThat(item.getStatus()).isEqualTo(ClearanceItemStatus.CLOSED);
    assertThat(item.getCloseReason())
        .isEqualTo(com.magampick.clearance.domain.ClearanceCloseReason.MANUAL);
    assertThat(response.status()).isEqualTo(ClearanceItemStatus.CLOSED);
  }

  @Test
  void 마감_임박_상품_수동_마감_이미_CLOSED_멱등() {
    // given
    Store store = store();
    Product prod = product(ProductStatus.ON_SALE);
    ClearanceItem item = ClearanceItemFixture.aClearanceItem(store, prod);
    ReflectionTestUtils.setField(item, "id", CLEARANCE_ITEM_ID);
    item.close();
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.of(item));
    given(clearanceItemMapper.toResponse(item))
        .willReturn(ClearanceItemFixture.aClosedResponse(CLEARANCE_ITEM_ID));

    // when
    ClearanceItemResponse response =
        clearanceItemService.closeClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID);

    // then
    assertThat(response.status()).isEqualTo(ClearanceItemStatus.CLOSED);
    assertThat(item.getStatus()).isEqualTo(ClearanceItemStatus.CLOSED);
  }

  @Test
  void 마감_임박_상품_수동_마감_없음_예외() {
    // given
    Store store = store();
    given(storeService.requireOwnedStore(SELLER_ID, STORE_ID)).willReturn(store);
    given(clearanceItemRepository.findByIdAndStoreId(CLEARANCE_ITEM_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> clearanceItemService.closeClearanceItem(SELLER_ID, STORE_ID, CLEARANCE_ITEM_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND);
  }
}
