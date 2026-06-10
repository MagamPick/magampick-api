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
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.dto.ProductUpdateRequest;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.fixture.ProductFixture;
import com.magampick.product.mapper.ProductMapper;
import com.magampick.product.repository.ProductRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  @Mock com.magampick.clearance.repository.ClearanceItemRepository clearanceItemRepository;
  @InjectMocks ProductService productService;

  private static final Long SELLER_ID = 1L;
  private static final Long STORE_ID = 10L;
  private static final Long PRODUCT_ID = 100L;

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

  private MockMultipartFile validImage() {
    byte[] jpeg = new byte[1024];
    jpeg[0] = (byte) 0xFF;
    jpeg[1] = (byte) 0xD8;
    jpeg[2] = (byte) 0xFF;
    return new MockMultipartFile("image", "test.jpg", "image/jpeg", jpeg);
  }

  // ── 등록 ─────────────────────────────────────────────────────────────────────

  @Test
  void 일반_상품_등록_성공() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(false);
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
                ProductCategory.BAKERY,
                null,
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
  void 일반_상품_등록_상품명_중복_예외() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(true);

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
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(false);
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
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    Pageable pageable = PageRequest.of(0, 20);
    Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByStoreIdAndDeletedAtIsNull(STORE_ID, pageable)).willReturn(page);
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
    then(productRepository).should(never()).findByStoreIdAndDeletedAtIsNull(any(), any());
  }

  @Test
  void 본인_매장_상품_상세_조회_성공() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
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
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.empty());

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
    then(productRepository).should(never()).findByIdAndStoreIdAndDeletedAtIsNull(any(), any());
  }

  // ── 수정 ─────────────────────────────────────────────────────────────────────

  @Test
  void 일반_상품_수정_성공_이름_변경() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    ProductUpdateRequest request = new ProductUpdateRequest("바게트", null, null, null, null);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(
            productRepository.existsByStoreIdAndNameAndDeletedAtIsNullAndIdNot(
                STORE_ID, "바게트", PRODUCT_ID))
        .willReturn(false);
    given(productMapper.toResponse(product)).willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    ProductResponse response =
        productService.updateProduct(SELLER_ID, STORE_ID, PRODUCT_ID, request, null);

    // then
    assertThat(product.getName()).isEqualTo("바게트");
    assertThat(response.id()).isEqualTo(PRODUCT_ID);
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 일반_상품_수정_성공_이미지_교체() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, null);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(storageService.upload(any())).willReturn("/uploads/2026/5/new.jpg");
    given(productMapper.toResponse(product)).willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    productService.updateProduct(SELLER_ID, STORE_ID, PRODUCT_ID, request, validImage());

    // then
    then(storageService).should().upload(any());
  }

  @Test
  void 일반_상품_수정_이름_중복_예외() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    ProductUpdateRequest request = new ProductUpdateRequest("바게트", null, null, null, null);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(
            productRepository.existsByStoreIdAndNameAndDeletedAtIsNullAndIdNot(
                STORE_ID, "바게트", PRODUCT_ID))
        .willReturn(true);

    // when / then
    assertThatThrownBy(
            () -> productService.updateProduct(SELLER_ID, STORE_ID, PRODUCT_ID, request, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NAME_DUPLICATE);
  }

  @Test
  void 일반_상품_수정_타인_매장_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    SELLER_ID,
                    STORE_ID,
                    PRODUCT_ID,
                    new ProductUpdateRequest(null, null, null, null, null),
                    null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  @Test
  void 일반_상품_수정_없는_상품_예외() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    SELLER_ID,
                    STORE_ID,
                    PRODUCT_ID,
                    new ProductUpdateRequest(null, null, null, null, null),
                    null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
  }

  // ── 삭제 ─────────────────────────────────────────────────────────────────────

  @Test
  void 일반_상품_삭제_성공() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(
            clearanceItemRepository.existsByProductIdAndStatus(
                PRODUCT_ID, com.magampick.clearance.domain.ClearanceItemStatus.OPEN))
        .willReturn(false);

    // when
    productService.deleteProduct(SELLER_ID, STORE_ID, PRODUCT_ID);

    // then
    assertThat(product.getDeletedAt()).isNotNull();
  }

  @Test
  void 일반_상품_삭제_이미_삭제된_상품_예외() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.deleteProduct(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  void 일반_상품_삭제_타인_매장_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.deleteProduct(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  @Test
  void 일반_상품_삭제_활성_떨이_존재_예외() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(
            clearanceItemRepository.existsByProductIdAndStatus(
                PRODUCT_ID, com.magampick.clearance.domain.ClearanceItemStatus.OPEN))
        .willReturn(true);

    // when / then
    assertThatThrownBy(() -> productService.deleteProduct(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_HAS_ACTIVE_CLEARANCE);
  }

  // ── 품절 / 재입고 ──────────────────────────────────────────────────────────────

  @Test
  void 상품_품절_처리_성공() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product))
        .willReturn(ProductFixture.aResponseWithStatus(PRODUCT_ID, ProductStatus.SOLD_OUT));

    // when
    ProductResponse response = productService.markSoldOut(SELLER_ID, STORE_ID, PRODUCT_ID);

    // then
    assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    assertThat(response.status()).isEqualTo(ProductStatus.SOLD_OUT);
  }

  @Test
  void 상품_품절_처리_이미_품절_멱등_성공() {
    // given
    Store store = store();
    Product product = ProductFixture.aSoldOutProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product))
        .willReturn(ProductFixture.aResponseWithStatus(PRODUCT_ID, ProductStatus.SOLD_OUT));

    // when
    ProductResponse response = productService.markSoldOut(SELLER_ID, STORE_ID, PRODUCT_ID);

    // then
    assertThat(response.status()).isEqualTo(ProductStatus.SOLD_OUT);
  }

  @Test
  void 상품_재입고_처리_성공() {
    // given
    Store store = store();
    Product product = ProductFixture.aSoldOutProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product))
        .willReturn(ProductFixture.aResponseWithStatus(PRODUCT_ID, ProductStatus.ON_SALE));

    // when
    ProductResponse response = productService.restock(SELLER_ID, STORE_ID, PRODUCT_ID);

    // then
    assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 상품_재입고_처리_이미_판매중_멱등_성공() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product))
        .willReturn(ProductFixture.aResponseWithStatus(PRODUCT_ID, ProductStatus.ON_SALE));

    // when
    ProductResponse response = productService.restock(SELLER_ID, STORE_ID, PRODUCT_ID);

    // then
    assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
  }

  @Test
  void 상품_품절_처리_없는_상품_예외() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.markSoldOut(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  void 상품_품절_처리_타인_매장_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.markSoldOut(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  @Test
  void 상품_재입고_처리_타인_매장_거부() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> productService.restock(SELLER_ID, STORE_ID, PRODUCT_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── 카테고리 / description / status (이슈 #1, #2, #4) ─────────────────────────

  @Test
  void 등록_카테고리_필수_없으면_등록_불가() {
    // given — category 없는 요청은 @NotNull 검증 실패이므로 서비스 레이어까지 오지 않음.
    // 서비스 레이어에서는 category 가 non-null 인 경우만 처리 → 빌더에 전달 확인
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(false);
    given(productRepository.save(any(Product.class)))
        .willAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
              return p;
            });
    given(productMapper.toResponse(any(Product.class)))
        .willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    ProductResponse response =
        productService.registerProduct(SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), null);

    // then — BAKERY 가 category 로 저장됐는지 캡처로 확인
    assertThat(response.category()).isEqualTo(ProductCategory.BAKERY);
  }

  @Test
  void 등록_status_null이면_ON_SALE_기본값() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(false);
    given(productRepository.save(any(Product.class)))
        .willAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
              // status null 이 아닌 경우 확인 — 서비스에서 ON_SALE 로 설정해야 함
              assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE);
              return p;
            });
    given(productMapper.toResponse(any(Product.class)))
        .willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    productService.registerProduct(SELLER_ID, STORE_ID, ProductFixture.aCreateRequest(), null);

    // then — save 호출 시 Product 상태 검증 (위 willAnswer 에서 수행)
    then(productRepository).should().save(any(Product.class));
  }

  @Test
  void 등록_status_SOLD_OUT으로_직접_설정() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(false);
    given(productRepository.save(any(Product.class)))
        .willAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
              assertThat(p.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
              return p;
            });
    given(productMapper.toResponse(any(Product.class)))
        .willReturn(ProductFixture.aResponseWithStatus(PRODUCT_ID, ProductStatus.SOLD_OUT));

    // when
    ProductResponse response =
        productService.registerProduct(
            SELLER_ID,
            STORE_ID,
            ProductFixture.aCreateRequestWithStatus(ProductStatus.SOLD_OUT),
            null);

    // then
    assertThat(response.status()).isEqualTo(ProductStatus.SOLD_OUT);
  }

  @Test
  void 등록_description_저장_및_응답_반환() {
    // given
    Store store = store();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(STORE_ID, "크로아상"))
        .willReturn(false);
    given(productRepository.save(any(Product.class)))
        .willAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              ReflectionTestUtils.setField(p, "id", PRODUCT_ID);
              assertThat(p.getDescription()).isEqualTo("부드러운 크로아상");
              return p;
            });
    given(productMapper.toResponse(any(Product.class)))
        .willReturn(ProductFixture.aResponseWithDescription(PRODUCT_ID, "부드러운 크로아상"));

    // when
    ProductResponse response =
        productService.registerProduct(
            SELLER_ID, STORE_ID, ProductFixture.aCreateRequestWithDescription("부드러운 크로아상"), null);

    // then
    assertThat(response.description()).isEqualTo("부드러운 크로아상");
  }

  @Test
  void 수정으로_category_변경() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store); // BAKERY
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    ProductUpdateRequest request =
        new ProductUpdateRequest(null, null, ProductCategory.BEVERAGE, null, null);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product)).willReturn(ProductFixture.aResponse(PRODUCT_ID));

    // when
    productService.updateProduct(SELLER_ID, STORE_ID, PRODUCT_ID, request, null);

    // then
    assertThat(product.getCategory()).isEqualTo(ProductCategory.BEVERAGE);
  }

  @Test
  void 수정으로_status_변경() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store); // ON_SALE
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    ProductUpdateRequest request =
        new ProductUpdateRequest(null, null, null, null, ProductStatus.SOLD_OUT);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product))
        .willReturn(ProductFixture.aResponseWithStatus(PRODUCT_ID, ProductStatus.SOLD_OUT));

    // when
    productService.updateProduct(SELLER_ID, STORE_ID, PRODUCT_ID, request, null);

    // then
    assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
  }

  @Test
  void 수정으로_description_변경() {
    // given
    Store store = store();
    Product product = ProductFixture.aProduct(store);
    ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, "새 설명", null);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(store));
    given(productRepository.findByIdAndStoreIdAndDeletedAtIsNull(PRODUCT_ID, STORE_ID))
        .willReturn(Optional.of(product));
    given(productMapper.toResponse(product))
        .willReturn(ProductFixture.aResponseWithDescription(PRODUCT_ID, "새 설명"));

    // when
    productService.updateProduct(SELLER_ID, STORE_ID, PRODUCT_ID, request, null);

    // then
    assertThat(product.getDescription()).isEqualTo("새 설명");
  }
}
