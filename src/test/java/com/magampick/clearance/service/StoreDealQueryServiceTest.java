package com.magampick.clearance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.StoreDealResponse;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductCategory;
import com.magampick.product.domain.ProductStatus;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreDealQueryServiceTest {

  @Mock ClearanceItemRepository clearanceItemRepository;
  @InjectMocks StoreDealQueryService service;

  private static final Long STORE_ID = 1L;

  // ── 활성(OPEN) 떨이만 반환 ─────────────────────────────────────────────────────────────────────

  @Test
  void 활성_OPEN_떨이만_반환() {
    ClearanceItem item = stubClearanceItem("크로아상 세트", 5000, 3000, null);
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of(item));

    List<StoreDealResponse> result = service.getActiveDeals(STORE_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("크로아상 세트");
  }

  @Test
  void 활성_떨이_없으면_빈_리스트() {
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of());

    List<StoreDealResponse> result = service.getActiveDeals(STORE_ID);

    assertThat(result).isEmpty();
  }

  // ── discountRate: fraction × 100 반올림 → int % ─────────────────────────────────────────────

  @Test
  void discountRate_40percent_반환() {
    // regularPrice 5000, salePrice 3000 → (1 - 3000/5000) = 0.40 → 40%
    ClearanceItem item = stubClearanceItem("아이템", 5000, 3000, null);
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of(item));

    List<StoreDealResponse> result = service.getActiveDeals(STORE_ID);

    assertThat(result.get(0).discountRate()).isEqualTo(40);
  }

  @Test
  void discountRate_33percent_반올림() {
    // regularPrice 3000, salePrice 2000 → (1 - 2000/3000) = 0.3333... → 33%
    ClearanceItem item = stubClearanceItem("아이템", 3000, 2000, null);
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of(item));

    List<StoreDealResponse> result = service.getActiveDeals(STORE_ID);

    assertThat(result.get(0).discountRate()).isEqualTo(33);
  }

  // ── imageUrl: product.imageUrl (null 허용) ─────────────────────────────────────────────────

  @Test
  void product_이미지_URL_반환() {
    Product product = stubProduct("/img/bread.jpg");
    ClearanceItem item = stubClearanceItem("빵", 4000, 2500, product);
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of(item));

    List<StoreDealResponse> result = service.getActiveDeals(STORE_ID);

    assertThat(result.get(0).imageUrl()).isEqualTo("/img/bread.jpg");
  }

  @Test
  void product_null_이면_imageUrl_null() {
    ClearanceItem item = stubClearanceItem("빵", 4000, 2500, null);
    given(clearanceItemRepository.findByStoreIdAndStatus(STORE_ID, ClearanceItemStatus.OPEN))
        .willReturn(List.of(item));

    List<StoreDealResponse> result = service.getActiveDeals(STORE_ID);

    assertThat(result.get(0).imageUrl()).isNull();
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  private Store stubStore() {
    Seller seller = Seller.builder().email("s@test.com").passwordHash("x").ownerName("사장").build();
    return Store.builder()
        .seller(seller)
        .businessNumber("1234567890")
        .name("테스트매장")
        .roadAddress("서울시 중구 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(37.5, 126.9))
        .phone("02-0000-0000")
        .operationStatus(OperationStatus.OPEN)
        .build();
  }

  private Product stubProduct(String imageUrl) {
    return Product.builder()
        .store(stubStore())
        .name("상품")
        .regularPrice(new BigDecimal("4000"))
        .imageUrl(imageUrl)
        .status(ProductStatus.ON_SALE)
        .category(ProductCategory.BAKERY)
        .build();
  }

  private ClearanceItem stubClearanceItem(String name, int regular, int sale, Product product) {
    return ClearanceItem.builder()
        .store(stubStore())
        .product(product)
        .name(name)
        .regularPrice(new BigDecimal(String.valueOf(regular)))
        .salePrice(new BigDecimal(String.valueOf(sale)))
        .totalQuantity(5)
        .pickupStartAt(LocalDateTime.now().minusHours(1))
        .pickupEndAt(LocalDateTime.now().plusHours(3))
        .build();
  }
}
