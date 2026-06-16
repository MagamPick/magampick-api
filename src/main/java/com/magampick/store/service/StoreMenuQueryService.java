package com.magampick.store.service;

import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.StoreMenuItemResponse;
import com.magampick.product.repository.ProductRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 메뉴 탭 조회 서비스. ON_SALE 상품(소프트 삭제 제외)만 반환. flat 리스트 — FE 가 category 로 그룹화. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMenuQueryService {

  private final ProductRepository productRepository;
  private final ClearanceItemRepository clearanceItemRepository;

  /**
   * 매장의 ON_SALE 상품 목록 반환.
   *
   * @param storeId 매장 ID
   * @return 판매중 메뉴 목록 (category 한국어 라벨 포함, hasActiveDeal 포함)
   */
  public List<StoreMenuItemResponse> getMenu(Long storeId) {
    List<Product> products =
        productRepository.findByStoreIdAndStatusAndDeletedAtIsNull(storeId, ProductStatus.ON_SALE);

    // N+1 방지: 매장의 OPEN 떨이 한 번 배치 조회 → productId Set으로 변환
    Set<Long> activeProductIds =
        clearanceItemRepository.findByStoreIdAndStatus(storeId, ClearanceItemStatus.OPEN).stream()
            .filter(c -> c.getProduct() != null)
            .map(c -> c.getProduct().getId())
            .collect(Collectors.toSet());

    return products.stream().map(p -> toResponse(p, activeProductIds.contains(p.getId()))).toList();
  }

  private StoreMenuItemResponse toResponse(Product product, boolean hasActiveDeal) {
    return new StoreMenuItemResponse(
        product.getId(),
        product.getName(),
        product.getImageUrl(),
        product.getRegularPrice(),
        product.getCategory().getLabel(),
        hasActiveDeal);
  }
}
