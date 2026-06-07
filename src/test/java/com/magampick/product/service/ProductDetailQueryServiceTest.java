package com.magampick.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.magampick.address.exception.AddressErrorCode;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.MenuProductDetailResponse;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.dto.StorePreviewInfo;
import com.magampick.store.service.StorePreviewHelper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductDetailQueryServiceTest {

  @Mock ProductRepository productRepository;
  @Mock StorePreviewHelper storePreviewHelper;
  @InjectMocks ProductDetailQueryService service;

  private static final Long PRODUCT_ID = 50L;
  private static final Long STORE_ID = 10L;
  private static final Long CUSTOMER_ID = 1L;

  // ── 없는 상품 → PRODUCT_NOT_FOUND ────────────────────────────────────────────────────────────────

  @Test
  void 없는_상품_PRODUCT_NOT_FOUND_예외() {
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail(PRODUCT_ID, CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
  }

  // ── 기본 주소지 없음 → DEFAULT_ADDRESS_REQUIRED ──────────────────────────────────────────────────

  @Test
  void 기본_주소지_없음_DEFAULT_ADDRESS_REQUIRED_예외() {
    Product product = stubProduct(ProductStatus.ON_SALE, "/img/bread.jpg");
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(product));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    assertThatThrownBy(() -> service.getDetail(PRODUCT_ID, CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 정상 조립 ────────────────────────────────────────────────────────────────────────────────────

  @Test
  void 정상_조회_menu_상세_조립() {
    Product product = stubProduct(ProductStatus.ON_SALE, "/img/bread.jpg");
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(product));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.8, "20:00"));

    MenuProductDetailResponse response = service.getDetail(PRODUCT_ID, CUSTOMER_ID);

    assertThat(response.kind()).isEqualTo("menu");
    assertThat(response.id()).isEqualTo(PRODUCT_ID);
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    assertThat(response.storeName()).isEqualTo("테스트매장");
    assertThat(response.distanceKm()).isEqualTo(0.8);
    assertThat(response.businessStatus()).isEqualTo(OperationStatus.OPEN);
    assertThat(response.imageUrl()).isEqualTo("/img/bread.jpg");
    assertThat(response.name()).isEqualTo("크로아상");
    assertThat(response.description()).isNull();
    assertThat(response.closingTime()).isEqualTo("20:00");
    assertThat(response.price()).isEqualByComparingTo("4500");
    assertThat(response.isOnSale()).isTrue();
  }

  // ── rating/reviewCount 항상 0 ─────────────────────────────────────────────────────────────────

  @Test
  void rating_항상_0() {
    Product product = stubProduct(ProductStatus.ON_SALE, null);
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(product));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));

    MenuProductDetailResponse response = service.getDetail(PRODUCT_ID, CUSTOMER_ID);

    assertThat(response.rating()).isEqualTo(0.0);
    assertThat(response.reviewCount()).isEqualTo(0L);
  }

  // ── isOnSale 매핑 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void isOnSale_ON_SALE_상품_true() {
    Product product = stubProduct(ProductStatus.ON_SALE, null);
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(product));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));

    MenuProductDetailResponse response = service.getDetail(PRODUCT_ID, CUSTOMER_ID);

    assertThat(response.isOnSale()).isTrue();
  }

  @Test
  void isOnSale_SOLD_OUT_상품_false() {
    Product product = stubProduct(ProductStatus.SOLD_OUT, null);
    given(productRepository.findByIdAndDeletedAtIsNull(PRODUCT_ID))
        .willReturn(Optional.of(product));
    given(storePreviewHelper.buildStorePreview(STORE_ID, CUSTOMER_ID))
        .willReturn(new StorePreviewInfo(0.5, null));

    MenuProductDetailResponse response = service.getDetail(PRODUCT_ID, CUSTOMER_ID);

    assertThat(response.isOnSale()).isFalse();
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

  private Product stubProduct(ProductStatus status, String imageUrl) {
    Product product =
        Product.builder()
            .store(stubStore())
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .imageUrl(imageUrl)
            .status(status)
            .category(ProductCategory.BAKERY)
            .build();
    setId(product, PRODUCT_ID);
    return product;
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
