package com.magampick.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.storage.StorageService;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.fixture.ProductFixture;
import com.magampick.product.mapper.ProductMapper;
import com.magampick.product.repository.ProductRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock ProductRepository productRepository;
  @Mock StoreRepository storeRepository;
  @Mock StorageService storageService;
  @Mock ProductMapper productMapper;
  @InjectMocks ProductService productService;

  private static final Long SELLER_ID = 1L;
  private static final Long STORE_ID = 10L;
  private static final Long PRODUCT_ID = 100L;

  private Seller seller() {
    Seller s =
        Seller.builder()
            .email("seller@test.com")
            .passwordHash("hash")
            .ownerName("홍길동")
            .businessNumber("1234567890")
            .build();
    ReflectionTestUtils.setField(s, "id", SELLER_ID);
    return s;
  }

  private Store store(StoreStatus status) {
    Store s =
        Store.builder()
            .seller(seller())
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .imageUrl("/uploads/store.jpg")
            .status(status)
            .build();
    ReflectionTestUtils.setField(s, "id", STORE_ID);
    return s;
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[1024]);
  }

  // ── 등록 ─────────────────────────────────────────────────────────────────────

  @Test
  void 일반_상품_등록_성공() {
    // given
    Store store = store(StoreStatus.APPROVED);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndName(STORE_ID, "크로아상")).willReturn(false);
    given(storageService.upload(any())).willReturn("/uploads/2026/5/product.jpg");
    given(productRepository.save(any(Product.class)))
        .willAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
              return p;
            });
    given(productMapper.toResponse(any(Product.class)))
        .willReturn(
            new ProductResponse(
                PRODUCT_ID,
                "크로아상",
                new BigDecimal("4500"),
                "/uploads/2026/5/product.jpg",
                ProductStatus.ON_SALE,
                OffsetDateTime.now()));

    // when
    ProductResponse response =
        productService.registerProduct(
            SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), validImage());

    // then
    assertThat(response.id()).isEqualTo(PRODUCT_ID);
    assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
    then(storageService).should().upload(any());
    then(productRepository).should().save(any(Product.class));
  }

  @Test
  void 일반_상품_등록_타인_매장_접근_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                productService.registerProduct(
                    SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
    then(storageService).should(never()).upload(any());
    then(productRepository).should(never()).save(any());
  }

  @Test
  void 일반_상품_등록_미승인_매장_거부() {
    // given
    Store pending = store(StoreStatus.PENDING);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID))
        .willReturn(Optional.of(pending));

    // when / then
    assertThatThrownBy(
            () ->
                productService.registerProduct(
                    SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_NOT_APPROVED);
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 일반_상품_등록_상품명_중복_예외() {
    // given
    Store store = store(StoreStatus.APPROVED);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndName(STORE_ID, "크로아상")).willReturn(true);

    // when / then
    assertThatThrownBy(
            () ->
                productService.registerProduct(
                    SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NAME_DUPLICATE);
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 일반_상품_등록_이미지_크기_초과_예외() {
    // given
    MockMultipartFile bigFile =
        new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

    // when / then
    assertThatThrownBy(
            () ->
                productService.registerProduct(
                    SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), bigFile))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_IMAGE_TOO_LARGE);
  }

  @Test
  void 일반_상품_등록_이미지_형식_불가_예외() {
    // given
    MockMultipartFile gifFile =
        new MockMultipartFile("image", "anim.gif", "image/gif", new byte[1024]);

    // when / then
    assertThatThrownBy(
            () ->
                productService.registerProduct(
                    SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), gifFile))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
  }

  @Test
  void 일반_상품_등록_이미지_없이_성공() {
    // given
    Store store = store(StoreStatus.APPROVED);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndName(STORE_ID, "크로아상")).willReturn(false);
    given(productRepository.save(any(Product.class)))
        .willAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
              return p;
            });
    given(productMapper.toResponse(any(Product.class)))
        .willReturn(ProductFixture.aResponseWithoutImage(PRODUCT_ID));

    // when
    ProductResponse response =
        productService.registerProduct(SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), null);

    // then
    assertThat(response.imageUrl()).isNull();
    then(storageService).should(never()).upload(any());
    then(productRepository).should().save(any(Product.class));
  }

  // ── 조회 ─────────────────────────────────────────────────────────────────────

  @Test
  void 본인_매장_상품_목록_조회_성공() {
    // given
    Store store = store(StoreStatus.APPROVED);
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    Pageable pageable = PageRequest.of(0, 20);
    Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByStoreId(STORE_ID, pageable)).willReturn(page);
    given(productMapper.toResponse(product)).willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    PageResponse<ProductResponse> result =
        productService.getMyStoreProducts(SELLER_ID, STORE_ID, pageable);

    // then
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).id()).isEqualTo(PRODUCT_ID);
    assertThat(result.totalCount()).isEqualTo(1);
  }

  @Test
  void 본인_매장_상품_목록_조회_타인_매장_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> productService.getMyStoreProducts(SELLER_ID, STORE_ID, PageRequest.of(0, 20)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
    then(productRepository).should(never()).findByStoreId(any(), any());
  }

  @Test
  void 본인_매장_상품_상세_조회_성공() {
    // given
    Store store = store(StoreStatus.APPROVED);
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreId(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product)).willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    ProductResponse response = productService.getMyStoreProduct(SELLER_ID, STORE_ID, PRODUCT_ID);

    // then
    assertThat(response.id()).isEqualTo(PRODUCT_ID);
  }

  @Test
  void 본인_매장_상품_상세_조회_없음_예외() {
    // given
    Store store = store(StoreStatus.APPROVED);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreId(PRODUCT_ID, STORE_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.getMyStoreProduct(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  void 본인_매장_상품_상세_조회_타인_매장_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.getMyStoreProduct(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
    then(productRepository).should(never()).findByIdAndStoreId(any(), any());
  }
}
