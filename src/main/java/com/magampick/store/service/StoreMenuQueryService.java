package com.magampick.store.service;

import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.StoreMenuItemResponse;
import com.magampick.product.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 메뉴 탭 조회 서비스. ON_SALE 상품(소프트 삭제 제외)만 반환. flat 리스트 — FE 가 category 로 그룹화. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMenuQueryService {

  private final ProductRepository productRepository;

  /**
   * 매장의 ON_SALE 상품 목록 반환.
   *
   * @param storeId 매장 ID
   * @return 판매중 메뉴 목록 (category 한국어 라벨 포함)
   */
  public List<StoreMenuItemResponse> getMenu(Long storeId) {
    List<Product> products =
        productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(storeId, ProductStatus.ON_SALE);
    return products.stream().map(this::toResponse).toList();
  }

  private StoreMenuItemResponse toResponse(Product product) {
    return new StoreMenuItemResponse(
        product.getId(),
        product.getName(),
        product.getImageUrl(),
        product.getRegularPrice(),
        product.getCategory().getLabel());
  }
}
