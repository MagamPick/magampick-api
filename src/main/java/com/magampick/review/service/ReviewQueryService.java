package com.magampick.review.service;

import com.magampick.global.response.SliceResponse;
import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderItem;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.repository.OrderRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.ReviewSummaryResponse;
import com.magampick.review.dto.ReviewableOrderResponse;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.mapper.ReviewMapper;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.store.service.StoreService;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

  private final ReviewRepository reviewRepository;
  private final ReviewMapper reviewMapper;
  private final OrderRepository orderRepository;
  private final StoreService storeService;

  // ── 엔드포인트용 ────────────────────────────────────────────────────────────────

  /** 매장 리뷰 목록 (Slice, 최신순, soft-delete 제외). */
  public SliceResponse<StoreReviewResponse> getStoreReviews(Long storeId, Pageable pageable) {
    return SliceResponse.of(
        reviewRepository
            .findByStoreIdOrderByCreatedAtDesc(storeId, pageable)
            .map(reviewMapper::toResponse));
  }

  /** 사장 본인 매장 리뷰 전체 목록 (최신순, soft-delete 제외). 소유권 검증 후 조회. */
  public List<StoreReviewResponse> getSellerStoreReviews(Long sellerId, Long storeId) {
    storeService.requireOwnedStore(sellerId, storeId);
    return reviewRepository.findByStoreIdWithCustomerOrderByCreatedAtDesc(storeId).stream()
        .map(reviewMapper::toResponse)
        .toList();
  }

  /** 매장 리뷰 요약 (평균 + 1~5점 분포). */
  public ReviewSummaryResponse getReviewSummary(Long storeId) {
    // 평점 통계 조회
    Object[] stats = reviewRepository.findStoreRatingStats(storeId).get(0);
    double average = stats[0] != null ? ((Number) stats[0]).doubleValue() : 0.0;
    long count = stats[1] != null ? ((Number) stats[1]).longValue() : 0L;

    // 별점 분포 조회
    List<Object[]> distRows = reviewRepository.findRatingDistribution(storeId);
    Map<Integer, Long> distMap =
        distRows.stream()
            .collect(
                Collectors.toMap(
                    row -> ((Number) row[0]).intValue(), row -> ((Number) row[1]).longValue()));

    // 분포 응답 변환
    List<ReviewSummaryResponse.StarCount> distribution =
        IntStream.rangeClosed(1, 5)
            .boxed()
            .sorted(Comparator.reverseOrder())
            .map(star -> new ReviewSummaryResponse.StarCount(star, distMap.getOrDefault(star, 0L)))
            .toList();

    return new ReviewSummaryResponse(count == 0 ? 0.0 : average, count, distribution);
  }

  // ── 소비자 리뷰 write 관련 조회 ─────────────────────────────────────────────────

  /** 소비자 COMPLETED 주문 목록 (리뷰 작성 가능 화면용). reviewed/reviewId 포함. */
  public List<ReviewableOrderResponse> getReviewableOrders(Long customerId) {
    // 완료 주문 조회
    List<Order> orders =
        orderRepository.findCompletedOrdersWithDetails(customerId, OrderStatus.COMPLETED);

    if (orders.isEmpty()) {
      return List.of();
    }

    // 주문별 리뷰 조회
    List<Long> orderIds = orders.stream().map(Order::getId).toList();
    Map<Long, Review> reviewByOrderId =
        reviewRepository.findByOrderIdInAndDeletedAtIsNull(orderIds).stream()
            .collect(Collectors.toMap(r -> r.getOrder().getId(), Function.identity()));

    // 응답 변환
    return orders.stream().map(order -> toReviewableOrderResponse(order, reviewByOrderId)).toList();
  }

  /** 주문별 리뷰 단건 조회. 리뷰 없으면 empty. */
  public Optional<MyReviewResponse> getOrderReview(Long customerId, Long orderId) {
    return reviewRepository
        .findByOrderIdAndDeletedAtIsNull(orderId)
        .map(reviewMapper::toMyReviewResponse);
  }

  /** 소비자 본인 리뷰 목록 (최신순, 삭제 제외). */
  public List<MyReviewResponse> getMyReviews(Long customerId) {
    return reviewRepository
        .findByCustomerIdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId)
        .stream()
        .map(reviewMapper::toMyReviewResponse)
        .toList();
  }

  private ReviewableOrderResponse toReviewableOrderResponse(
      Order order, Map<Long, Review> reviewByOrderId) {
    List<ReviewableOrderResponse.OrderedItem> items =
        order.getOrderItems().stream().map(this::toOrderedItem).toList();

    Review review = reviewByOrderId.get(order.getId());
    boolean reviewed = review != null;
    Long reviewId = reviewed ? review.getId() : null;

    var pickedUpAt =
        order.getCompletedAt() != null
            ? order.getCompletedAt().atOffset(ZoneOffset.ofHours(9))
            : null;

    return new ReviewableOrderResponse(
        order.getId(),
        order.getStore().getId(),
        order.getStore().getName(),
        items,
        pickedUpAt,
        reviewed,
        reviewId);
  }

  private ReviewableOrderResponse.OrderedItem toOrderedItem(OrderItem oi) {
    Long productId;
    String kind;
    if (oi.getItemKind() == com.magampick.order.domain.ItemKind.DEAL) {
      productId = oi.getClearanceItem().getId();
      kind = "deal";
    } else {
      productId = oi.getProduct().getId();
      kind = "menu";
    }
    return new ReviewableOrderResponse.OrderedItem(productId, kind, oi.getName());
  }

  // ── Phase 4 탐색 기능 주입용 집계 ───────────────────────────────────────────────

  /**
   * 매장 평점/건수 (soft-delete 제외). 0건이면 {@link RatingStats#EMPTY}.
   *
   * @param storeId 매장 ID
   */
  public RatingStats getStoreRating(Long storeId) {
    return toRatingStats(reviewRepository.findStoreRatingStats(storeId).get(0));
  }

  /**
   * 매장 배치 평점 조회 — N+1 방지. 결과 없는 storeId 는 Map 에 포함 안 됨 → 호출 측에서 기본값 처리.
   *
   * @param storeIds 매장 ID 목록
   * @return storeId → RatingStats Map
   */
  public Map<Long, RatingStats> getStoreRatings(Collection<Long> storeIds) {
    if (storeIds == null || storeIds.isEmpty()) {
      return Map.of();
    }
    return reviewRepository.findStoreRatingsStatsBatch(storeIds).stream()
        .collect(
            Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row ->
                    new RatingStats(
                        ((Number) row[1]).doubleValue(), ((Number) row[2]).longValue())));
  }

  /**
   * 떨이 상품 평점/건수 (soft-delete 제외). 상품 평점 = 해당 떨이를 포함한 주문 리뷰 평균.
   *
   * @param clearanceItemId 떨이 상품 ID
   */
  public RatingStats getClearanceItemRating(Long clearanceItemId) {
    return toRatingStats(reviewRepository.findClearanceItemRatingStats(clearanceItemId).get(0));
  }

  /**
   * 일반 상품 평점/건수 (soft-delete 제외). 상품 평점 = 해당 상품을 포함한 주문 리뷰 평균.
   *
   * @param productId 일반 상품 ID
   */
  public RatingStats getMenuProductRating(Long productId) {
    return toRatingStats(reviewRepository.findMenuProductRatingStats(productId).get(0));
  }

  // ── 내부 유틸 ───────────────────────────────────────────────────────────────────

  private RatingStats toRatingStats(Object[] stats) {
    long count = stats[1] != null ? ((Number) stats[1]).longValue() : 0L;
    if (count == 0) {
      return RatingStats.EMPTY;
    }
    double average = ((Number) stats[0]).doubleValue();
    return new RatingStats(average, count);
  }
}
