package com.magampick.clearance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.magampick.address.exception.AddressErrorCode;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.DealProductDetailResponse;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.dto.StorePreviewInfo;
import com.magampick.store.service.StorePreviewHelper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClearanceItemDetailQueryServiceTest {

  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock ReviewQueryService reviewQueryService;
  @Mock StorePreviewHelper storePreviewHelper;
  @InjectMocks ClearanceItemDetailQueryService service;

  private static final Long ITEM_ID = 100L;
  private static final Long STORE_ID = 10L;
  private static final Long CUSTOMER_ID = 1L;

  // ── 없는 떨이 → NOT_FOUND ──────────────────────────────────────────────────────────────────────

  @Test
  void 없는_떨이_CLEARANCE_ITEM_NOT_FOUND_예외() {
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail(ITEM_ID, CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND);
  }

  // ── 기본 주소지 없음 → DEFAULT_ADDRESS_REQUIRED ──────────────────────────────────────────────────

  @Test
  void 기본_주소지_없음_DEFAULT_ADDRESS_REQUIRED_예외() {
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, futurePickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    assertThatThrownBy(() -> service.getDetail(ITEM_ID, CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 정상 조립 ────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 정상_조회_deal_상세_조립() {
    Product product = stubProduct("/img/item.jpg");
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, futurePickupEnd(), product);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(1.2, "21:00"));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(new RatingStats(4.0, 5L));

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.kind()).isEqualTo("deal");
    assertThat(response.id()).isEqualTo(ITEM_ID);
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    assertThat(response.storeName()).isEqualTo("테스트매장");
    assertThat(response.distanceKm()).isEqualTo(1.2);
    assertThat(response.businessStatus()).isEqualTo(OperationStatus.OPEN);
    assertThat(response.imageUrl()).isEqualTo("/img/item.jpg");
    assertThat(response.name()).isEqualTo("크로아상");
    assertThat(response.description()).isNull(); // product 에 description 없으면 null
    assertThat(response.rating()).isEqualTo(4.0);
    assertThat(response.reviewCount()).isEqualTo(5L);
    assertThat(response.closingTime()).isEqualTo("21:00");
    assertThat(response.originalPrice()).isEqualByComparingTo("4500");
    assertThat(response.salePrice()).isEqualByComparingTo("3000");
  }

  // ── dealStatus 매핑 4케이스 ───────────────────────────────────────────────────────────────────────

  @Test
  void dealStatus_OPEN_픽업마감전_ACTIVE() {
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, futurePickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.dealStatus()).isEqualTo("ACTIVE");
  }

  @Test
  void dealStatus_OPEN_픽업마감후_EXPIRED() {
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, pastPickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.dealStatus()).isEqualTo("EXPIRED");
  }

  @Test
  void dealStatus_SOLD_OUT() {
    ClearanceItem item = stubItem(ClearanceItemStatus.SOLD_OUT, futurePickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.dealStatus()).isEqualTo("SOLD_OUT");
  }

  @Test
  void dealStatus_CLOSED_EXPIRED() {
    ClearanceItem item = stubItem(ClearanceItemStatus.CLOSED, futurePickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.dealStatus()).isEqualTo("EXPIRED");
  }

  // ── discountRate 퍼센트 계산 ───────────────────────────────────────────────────────────────────

  @Test
  void discountRate_40percent() {
    // regularPrice 5000, salePrice 3000 → (1 - 3000/5000) = 0.40 → 40%
    ClearanceItem item = stubItemWithPrices(new BigDecimal("5000"), new BigDecimal("3000"));
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.discountRate()).isEqualTo(40);
  }

  // ── imageUrl: product null → null ─────────────────────────────────────────────────────────────

  @Test
  void imageUrl_product_없으면_null() {
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, futurePickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.imageUrl()).isNull();
  }

  // ── description 배선 ──────────────────────────────────────────────────────────────────────────

  @Test
  void description_product_있으면_product_description_반환() {
    Product product = stubProductWithDescription("/img/item.jpg", "맛있는 크로아상");
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, futurePickupEnd(), product);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(1.2, "21:00"));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.description()).isEqualTo("맛있는 크로아상");
  }

  @Test
  void description_product_없으면_null() {
    ClearanceItem item = stubItem(ClearanceItemStatus.OPEN, futurePickupEnd(), null);
    given(clearanceItemRepository.findByIdWithStoreAndProduct(ITEM_ID))
        .willReturn(Optional.of(item));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));
    given(reviewQueryService.getClearanceItemRating(ITEM_ID)).willReturn(RatingStats.EMPTY);

    DealProductDetailResponse response = service.getDetail(ITEM_ID, CUSTOMER_ID);

    assertThat(response.description()).isNull();
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private Store stubStore() {
    Seller seller = Seller.builder().email("s@test.com").passwordHash("x").ownerName("사장").build();
    Store store =
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name("테스트매장")
            .roadAddress("서울시 중구 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(37.5665, 126.9780))
            .phone("02-0000-0000")
            .operationStatus(OperationStatus.OPEN)
            .build();
    setId(store, STORE_ID);
    return store;
  }

  private Product stubProduct(String imageUrl) {
    return Product.builder()
        .store(stubStore())
        .name("상품")
        .regularPrice(new BigDecimal("4500"))
        .imageUrl(imageUrl)
        .status(ProductStatus.ON_SALE)
        .category(ProductCategory.BAKERY)
        .build();
  }

  private Product stubProductWithDescription(String imageUrl, String description) {
    return Product.builder()
        .store(stubStore())
        .name("상품")
        .regularPrice(new BigDecimal("4500"))
        .imageUrl(imageUrl)
        .status(ProductStatus.ON_SALE)
        .category(ProductCategory.BAKERY)
        .description(description)
        .build();
  }

  private ClearanceItem stubItem(
      ClearanceItemStatus status, LocalDateTime pickupEndAt, Product product) {
    ClearanceItem item =
        ClearanceItem.builder()
            .store(stubStore())
            .product(product)
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .salePrice(new BigDecimal("3000"))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(2))
            .pickupEndAt(pickupEndAt)
            .build();
    setId(item, ITEM_ID);
    if (status == ClearanceItemStatus.SOLD_OUT) {
      soldOut(item);
    } else if (status == ClearanceItemStatus.CLOSED) {
      item.close();
    }
    return item;
  }

  private ClearanceItem stubItemWithPrices(BigDecimal regular, BigDecimal sale) {
    ClearanceItem item =
        ClearanceItem.builder()
            .store(stubStore())
            .product(null)
            .name("크로아상")
            .regularPrice(regular)
            .salePrice(sale)
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.now().minusHours(2))
            .pickupEndAt(futurePickupEnd())
            .build();
    setId(item, ITEM_ID);
    return item;
  }

  /** ClearanceItem 에 SOLD_OUT 상태를 강제 주입 (도메인 메서드 없으므로 reflection). */
  private void soldOut(ClearanceItem item) {
    try {
      Field f = ClearanceItem.class.getDeclaredField("status");
      f.setAccessible(true);
      f.set(item, ClearanceItemStatus.SOLD_OUT);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private LocalDateTime futurePickupEnd() {
    return LocalDateTime.now().plusHours(3);
  }

  private LocalDateTime pastPickupEnd() {
    return LocalDateTime.now().minusHours(1);
  }

  private void setId(Object entity, Long id) {
    try {
      Field field = getField(entity.getClass(), "id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (Exception e) {
      throw new RuntimeException("id 주입 실패", e);
    }
  }

  private Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      if (clazz.getSuperclass() != null) {
        return getField(clazz.getSuperclass(), name);
      }
      throw e;
    }
  }
}
