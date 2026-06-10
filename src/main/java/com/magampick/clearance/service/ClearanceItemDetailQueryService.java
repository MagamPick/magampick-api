package com.magampick.clearance.service;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.dto.DealProductDetailResponse;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.dto.StorePreviewInfo;
import com.magampick.store.service.StorePreviewHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 떨이 상품 상세 조회 서비스. deal(ClearanceItem) 단건 + 매장 미리보기 + 평점 + dealStatus 조립.
 *
 * <p>dealStatus 매핑: OPEN(+픽업마감전) → ACTIVE, OPEN(+픽업마감후) → EXPIRED, SOLD_OUT → SOLD_OUT, CLOSED →
 * EXPIRED.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClearanceItemDetailQueryService {

  /** 픽업 마감 시각 비교 기준 시간대 = KST. */
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ClearanceItemRepository clearanceItemRepository;
  private final ReviewQueryService reviewQueryService;
  private final StorePreviewHelper storePreviewHelper;

  /**
   * 떨이 상품 상세 조회.
   *
   * @param clearanceItemId 떨이 상품 ID
   * @param customerId 소비자 ID (거리 origin 계산에 사용)
   * @throws BusinessException CLEARANCE_ITEM_NOT_FOUND — 떨이 없음
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없음
   */
  public DealProductDetailResponse getDetail(Long clearanceItemId, Long customerId) {
    // 1. 떨이 조회 (store + product fetch)
    ClearanceItem item =
        clearanceItemRepository
            .findByIdWithStoreAndProduct(clearanceItemId)
            .orElseThrow(
                () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));

    // 2. 매장 미리보기 (거리 + closingTime)
    StorePreviewInfo storePreview =
        storePreviewHelper.buildStorePreview(item.getStore().getId(), customerId);

    // 3. 평점
    RatingStats ratingStats = reviewQueryService.getClearanceItemRating(clearanceItemId);

    // 4. discountRate (fraction × 100 반올림 → int %)
    int discountRate =
        item.getDiscountRate()
            .multiply(new BigDecimal("100"))
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();

    // 5. imageUrl, description (product 연결 없으면 null)
    String imageUrl = item.getProduct() != null ? item.getProduct().getImageUrl() : null;
    String description = item.getProduct() != null ? item.getProduct().getDescription() : null;

    // 6. dealStatus 매핑
    String dealStatus = resolveDealStatus(item);

    return new DealProductDetailResponse(
        "deal",
        item.getId(),
        item.getStore().getId(),
        item.getStore().getName(),
        storePreview.distanceKm(),
        item.getStore().getOperationStatus(),
        imageUrl,
        item.getName(),
        description,
        ratingStats.average(),
        ratingStats.count(),
        storePreview.closingTime(),
        item.getRegularPrice(),
        item.getSalePrice(),
        discountRate,
        item.getPickupEndAt(),
        item.getRemainingQuantity(),
        dealStatus);
  }

  /**
   * BE ClearanceItemStatus → FE dealStatus 문자열 매핑.
   *
   * <ul>
   *   <li>OPEN + pickupEndAt &lt; now(KST) → "EXPIRED"
   *   <li>OPEN → "ACTIVE"
   *   <li>SOLD_OUT → "SOLD_OUT"
   *   <li>CLOSED → "EXPIRED"
   * </ul>
   */
  private String resolveDealStatus(ClearanceItem item) {
    if (item.isSoldOut()) {
      return "SOLD_OUT";
    }
    if (item.isClosed()) {
      return "EXPIRED";
    }
    // OPEN
    LocalDateTime now = LocalDateTime.now(KST);
    return item.getPickupEndAt().isBefore(now) ? "EXPIRED" : "ACTIVE";
  }
}
