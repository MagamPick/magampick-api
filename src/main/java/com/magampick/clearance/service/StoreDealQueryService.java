package com.magampick.clearance.service;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.StoreDealResponse;
import com.magampick.clearance.repository.ClearanceItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 마감할인 탭 조회 서비스. 활성(OPEN) 떨이만 반환.
 *
 * <p>discountRate = {@code ClearanceItem.getDiscountRate()} × 100 반올림 (FE discountRate = %).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreDealQueryService {

  private final ClearanceItemRepository clearanceItemRepository;

  /**
   * 매장의 활성 마감할인 목록 반환 (status = OPEN 만).
   *
   * @param storeId 매장 ID
   * @return 활성 떨이 카드 목록
   */
  public List<StoreDealResponse> getActiveDeals(Long storeId) {
    List<ClearanceItem> items =
        clearanceItemRepository.findByStoreIdAndStatus(storeId, ClearanceItemStatus.OPEN);
    return items.stream().map(this::toResponse).toList();
  }

  private StoreDealResponse toResponse(ClearanceItem item) {
    // discountRate fraction (e.g. 0.40) × 100 → int %
    int discountRate =
        item.getDiscountRate()
            .multiply(new BigDecimal("100"))
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();

    String imageUrl = item.getProduct() != null ? item.getProduct().getImageUrl() : null;

    return new StoreDealResponse(
        item.getId(),
        item.getName(),
        imageUrl,
        discountRate,
        item.getRegularPrice(),
        item.getSalePrice(),
        item.getPickupEndAt(),
        item.getRemainingQuantity());
  }
}
