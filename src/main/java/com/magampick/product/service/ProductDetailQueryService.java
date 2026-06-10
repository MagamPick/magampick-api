package com.magampick.product.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.product.domain.Product;
import com.magampick.product.dto.MenuProductDetailResponse;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.store.dto.StorePreviewInfo;
import com.magampick.store.service.StorePreviewHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 일반 상품 상세 조회 서비스. menu(Product) 단건 + 매장 미리보기 조립.
 *
 * <p>일반 상품은 주문 대상이 아니므로 rating=0.0, reviewCount=0.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductDetailQueryService {

  private final ProductRepository productRepository;
  private final StorePreviewHelper storePreviewHelper;

  /**
   * 일반 상품 상세 조회.
   *
   * @param productId 상품 ID
   * @param customerId 소비자 ID (거리 origin 계산에 사용)
   * @throws BusinessException PRODUCT_NOT_FOUND — 상품 없음/삭제
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없음
   */
  public MenuProductDetailResponse getDetail(Long productId, Long customerId) {
    // 1. 상품 조회 (소프트 삭제 제외, store fetch)
    Product product =
        productRepository
            .findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    // 2. 매장 미리보기 (거리 + closingTime)
    StorePreviewInfo storePreview =
        storePreviewHelper.buildStorePreview(product.getStore().getId(), customerId);

    return new MenuProductDetailResponse(
        "menu",
        product.getId(),
        product.getStore().getId(),
        product.getStore().getName(),
        storePreview.distanceKm(),
        product.getStore().getOperationStatus(),
        product.getImageUrl(),
        product.getName(),
        product.getDescription(),
        0.0,
        0L,
        storePreview.closingTime(),
        product.getRegularPrice(),
        product.isOnSale());
  }
}
