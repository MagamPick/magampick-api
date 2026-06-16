package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.StoreMenuItemResponse;
import com.magampick.product.repository.ProductRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreMenuQueryServiceTest {

  @Mock ProductRepository productRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @InjectMocks StoreMenuQueryService service;

  private static final Long STORE_ID = 1L;

  // ── ON_SALE 상품만 반환 ─────────────────────────────────────────────────────────────────────────

  @Test
  void ON_SALE_상품만_반환() {
    Product product = stubProduct("크로아상", 3500, ProductCategory.BAKERY, ProductStatus.ON_SALE);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("크로아상");
  }

  @Test
  void 상품_없으면_빈_리스트() {
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of());
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result).isEmpty();
  }

  // ── category 한국어 라벨 ─────────────────────────────────────────────────────────────────────

  @Test
  void BAKERY_카테고리_라벨_베이커리() {
    Product product = stubProduct("빵", 3000, ProductCategory.BAKERY, ProductStatus.ON_SALE);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result.get(0).category()).isEqualTo("베이커리");
  }

  @Test
  void BEVERAGE_카테고리_라벨_음료() {
    Product product = stubProduct("아메리카노", 2500, ProductCategory.BEVERAGE, ProductStatus.ON_SALE);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result.get(0).category()).isEqualTo("음료");
  }

  @Test
  void DESSERT_카테고리_라벨_디저트() {
    Product product = stubProduct("마카롱", 1800, ProductCategory.DESSERT, ProductStatus.ON_SALE);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result.get(0).category()).isEqualTo("디저트");
  }

  @Test
  void ETC_카테고리_라벨_기타() {
    Product product = stubProduct("세트", 10000, ProductCategory.ETC, ProductStatus.ON_SALE);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result.get(0).category()).isEqualTo("기타");
  }

  // ── hasActiveDeal ─────────────────────────────────────────────────────────────────────────────

  @Test
  void 활성떨이_있는_상품_hasActiveDeal_true() {
    Product product = stubProduct("크로아상", 3500, ProductCategory.BAKERY, ProductStatus.ON_SALE);
    ReflectionTestUtils.setField(product, "id", 99L);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of(stubClearanceItem(product)));

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result.get(0).hasActiveDeal()).isTrue();
  }

  @Test
  void 활성떨이_없는_상품_hasActiveDeal_false() {
    Product product = stubProduct("크로아상", 3500, ProductCategory.BAKERY, ProductStatus.ON_SALE);
    given(
            productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(
                STORE_ID, ProductStatus.ON_SALE))
        .willReturn(List.of(product));
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreMenuItemResponse> result = service.getMenu(STORE_ID);

    assertThat(result.get(0).hasActiveDeal()).isFalse();
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private ClearanceItem stubClearanceItem(Product product) {
    ClearanceItem ci =
        ClearanceItem.builder()
            .store(stubStore())
            .product(product)
            .name(product.getName())
            .regularPrice(product.getRegularPrice())
            .salePrice(product.getRegularPrice().multiply(new BigDecimal("0.7")))
            .totalQuantity(5)
            .pickupStartAt(LocalDateTime.of(2099, 1, 1, 9, 0))
            .pickupEndAt(LocalDateTime.of(2099, 1, 1, 21, 0))
            .build();
    ReflectionTestUtils.setField(ci, "status", ClearanceItemStatus.OPEN);
    return ci;
  }

  private Store stubStore() {
    Seller seller = Seller.builder().email("s@test.com").passwordHash("x").ownerName("사장").build();
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .representativeName("홍길동")
        .openDate(LocalDate.of(2024, 3, 15))
        .name("테스트매장")
        .roadAddress("서울시 중구 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(37.5, 126.9))
        .phone("02-0000-0000")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private Product stubProduct(
      String name, int price, ProductCategory category, ProductStatus status) {
    return Product.builder()
        .store(stubStore())
        .name(name)
        .regularPrice(new BigDecimal(String.valueOf(price)))
        .status(status)
        .category(category)
        .build();
  }
}
