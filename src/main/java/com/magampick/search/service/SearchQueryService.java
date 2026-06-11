package com.magampick.search.service;

import com.magampick.address.service.AddressService;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.clearance.repository.DealSearchCandidate;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.product.repository.ProductRepository;
import com.magampick.product.repository.ProductSearchCandidate;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.search.dto.SearchProductItemResponse;
import com.magampick.search.dto.SearchProductItemResponse.DealSearchItem;
import com.magampick.search.dto.SearchProductItemResponse.MenuSearchItem;
import com.magampick.search.dto.SearchResultResponse;
import com.magampick.search.dto.SearchSuggestionResponse;
import com.magampick.search.dto.SuggestionKind;
import com.magampick.store.dto.StoreListItemResponse;
import com.magampick.store.dto.StoreSort;
import com.magampick.store.repository.StoreCandidate;
import com.magampick.store.repository.StoreRepository;
import com.magampick.store.service.StoreQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 9 검색 서비스. 전체 매장 목록과 동일한 노출 세트(5km·OPEN·오늘영업·기본주소지) 를 기반으로 키워드 검색 및 자동완성을 제공한다.
 *
 * <p>매장 섹션: 매장명 ILIKE 부분일치 → 노출 세트에서 필터링. 상품 섹션: 떨이(OPEN, kind=deal) + 메뉴(ON_SALE, kind=menu) 이름
 * 부분일치.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchQueryService {

  private static final double AUTOCOMPLETE_THRESHOLD = 0.3;
  private static final int AUTOCOMPLETE_MAX = 10;

  private final AddressService addressService;
  private final StoreRepository storeRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final ProductRepository productRepository;
  private final FavoriteRepository favoriteRepository;
  private final ReviewQueryService reviewQueryService;

  /**
   * 키워드 검색. 빈/공백 키워드는 즉시 빈 결과 반환.
   *
   * @param customerId 소비자 ID
   * @param q 검색 키워드
   * @param sort 정렬 기준
   * @return 검색 결과 (stores + products)
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없을 때
   */
  public SearchResultResponse search(Long customerId, String q, StoreSort sort) {
    // 빈 키워드 fast-exit
    if (q == null || q.trim().isEmpty()) {
      return new SearchResultResponse(List.of(), List.of());
    }

    // origin 추출
    Point defaultLocation = addressService.requireDefaultLocation(customerId);
    double lat = GeometryUtil.latitude(defaultLocation);
    double lng = GeometryUtil.longitude(defaultLocation);
    String today = LocalDate.now().getDayOfWeek().name();
    String trimmed = q.trim();
    String escaped = escapeLike(trimmed);

    // 1. 노출 세트 전체 후보 (enrich 에 필요)
    List<StoreCandidate> allCandidates = storeRepository.findOpenStoresWithin5km(lat, lng, today);
    if (allCandidates.isEmpty()) {
      return new SearchResultResponse(List.of(), List.of());
    }

    List<Long> allStoreIds = allCandidates.stream().map(StoreCandidate::getId).toList();

    // 2. 배치 enrich
    Map<Long, RatingStats> ratingMap = reviewQueryService.getStoreRatings(allStoreIds);
    Map<Long, Object[]> dealSummaryMap = buildDealSummaryMap(allStoreIds);
    Set<Long> favoriteIds =
        new HashSet<>(
            favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(customerId, allStoreIds));

    // storeId → (distanceKm, rating, activeDealCount, maxDiscountRate, nearestPickupEndAt) 조회용
    Map<Long, StoreCandidate> candidateMap =
        allCandidates.stream().collect(Collectors.toMap(StoreCandidate::getId, c -> c));

    // 3. 매장 섹션: 매장명 일치 storeId 목록
    Set<Long> matchedStoreIds =
        new HashSet<>(storeRepository.findStoreIdsWithin5kmMatchingName(lat, lng, today, escaped));

    List<StoreListItemResponse> storeSection =
        buildStoreSection(
            allCandidates, matchedStoreIds, ratingMap, dealSummaryMap, favoriteIds, sort);

    // 4. 상품 섹션: 전체 후보 매장 내 이름 일치
    List<DealSearchCandidate> dealResults =
        clearanceItemRepository.searchOpenDealsByStoreIds(allStoreIds, escaped);
    List<ProductSearchCandidate> menuResults =
        productRepository.searchOnSaleProductsByStoreIds(allStoreIds, escaped);

    // storeId → storeName 매핑
    Map<Long, String> storeNameMap =
        allCandidates.stream()
            .collect(Collectors.toMap(StoreCandidate::getId, StoreCandidate::getName));

    List<SearchProductItemResponse> productSection =
        buildProductSection(dealResults, menuResults, storeNameMap, candidateMap, ratingMap, sort);

    return new SearchResultResponse(storeSection, productSection);
  }

  /**
   * 자동완성. 1자 미만(trim 후) 이면 빈 결과 반환.
   *
   * @param customerId 소비자 ID
   * @param q 자동완성 입력어
   * @return 최대 10개 제안 (유사도 내림차순, 텍스트 dedup)
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없을 때
   */
  public List<SearchSuggestionResponse> autocomplete(Long customerId, String q) {
    if (q == null || q.trim().length() < 1) {
      return List.of();
    }

    String trimmed = q.trim();

    // origin 추출
    Point defaultLocation = addressService.requireDefaultLocation(customerId);
    double lat = GeometryUtil.latitude(defaultLocation);
    double lng = GeometryUtil.longitude(defaultLocation);
    String today = LocalDate.now().getDayOfWeek().name();

    // 노출 세트 매장 ID 목록
    List<StoreCandidate> candidates = storeRepository.findOpenStoresWithin5km(lat, lng, today);
    List<Long> storeIds = candidates.stream().map(StoreCandidate::getId).toList();

    // 매장명, 떨이명, 상품명 제안 수집 — similarity 포함
    // (name → {maxSimilarity, kind}) — 같은 이름이면 더 높은 유사도 유지
    Map<String, ScoredSuggestion> merged = new LinkedHashMap<>();

    storeRepository
        .suggestStoreNamesWithin5km(lat, lng, today, trimmed, AUTOCOMPLETE_THRESHOLD)
        .forEach(
            s ->
                merged.merge(
                    s.getName().toLowerCase(),
                    new ScoredSuggestion(s.getName(), s.getSimilarity(), SuggestionKind.STORE),
                    (a, b) -> a.similarity() >= b.similarity() ? a : b));

    if (!storeIds.isEmpty()) {
      clearanceItemRepository
          .suggestDealNamesByStoreIds(storeIds, trimmed, AUTOCOMPLETE_THRESHOLD)
          .forEach(
              s ->
                  merged.merge(
                      s.getName().toLowerCase(),
                      new ScoredSuggestion(s.getName(), s.getSimilarity(), SuggestionKind.PRODUCT),
                      (a, b) -> a.similarity() >= b.similarity() ? a : b));

      productRepository
          .suggestProductNamesByStoreIds(storeIds, trimmed, AUTOCOMPLETE_THRESHOLD)
          .forEach(
              s ->
                  merged.merge(
                      s.getName().toLowerCase(),
                      new ScoredSuggestion(s.getName(), s.getSimilarity(), SuggestionKind.PRODUCT),
                      (a, b) -> a.similarity() >= b.similarity() ? a : b));
    }

    // 제안 정렬 및 반환
    return merged.values().stream()
        .sorted(
            Comparator.comparingDouble(ScoredSuggestion::similarity)
                .reversed()
                .thenComparing(ScoredSuggestion::displayName))
        .limit(AUTOCOMPLETE_MAX)
        .map(s -> new SearchSuggestionResponse(s.kind(), s.displayName()))
        .toList();
  }

  // ── private helpers ──────────────────────────────────────────────────────────────────────────

  private Map<Long, Object[]> buildDealSummaryMap(List<Long> storeIds) {
    return clearanceItemRepository
        .findActiveDealSummaryByStoreIds(storeIds, ClearanceItemStatus.OPEN)
        .stream()
        .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> row));
  }

  /** ILIKE 메타문자(\ % _) 이스케이프 — 입력을 리터럴로 매칭(FE .includes 정합). SQL 의 ESCAPE '\' 와 함께 사용. */
  private static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** 매장 섹션 구성: allCandidates 에서 matchedStoreIds 필터 → enrich → sort. */
  private List<StoreListItemResponse> buildStoreSection(
      List<StoreCandidate> allCandidates,
      Set<Long> matchedStoreIds,
      Map<Long, RatingStats> ratingMap,
      Map<Long, Object[]> dealSummaryMap,
      Set<Long> favoriteIds,
      StoreSort sort) {

    List<EnrichedSearchStore> matched =
        allCandidates.stream()
            .filter(c -> matchedStoreIds.contains(c.getId()))
            .map(c -> enrich(c, ratingMap, dealSummaryMap, favoriteIds))
            .toList();

    return sortStores(matched, sort).stream().map(EnrichedSearchStore::toResponse).toList();
  }

  private EnrichedSearchStore enrich(
      StoreCandidate c,
      Map<Long, RatingStats> ratingMap,
      Map<Long, Object[]> dealSummaryMap,
      Set<Long> favoriteIds) {

    double distanceKm = c.getDistanceMeters() / 1000.0;
    RatingStats rating = ratingMap.getOrDefault(c.getId(), RatingStats.EMPTY);
    Object[] deal = dealSummaryMap.get(c.getId());
    long activeDealCount = deal != null ? ((Number) deal[1]).longValue() : 0L;
    double maxDiscountRate = deal != null ? ((BigDecimal) deal[2]).doubleValue() : 0.0;
    LocalDateTime nearestPickupEndAt = deal != null ? (LocalDateTime) deal[3] : null;
    boolean isFavorite = favoriteIds.contains(c.getId());

    return new EnrichedSearchStore(
        c.getId(),
        c.getName(),
        c.getImageUrl(),
        distanceKm,
        rating.average(),
        activeDealCount,
        maxDiscountRate,
        nearestPickupEndAt,
        isFavorite);
  }

  private List<EnrichedSearchStore> sortStores(List<EnrichedSearchStore> stores, StoreSort sort) {
    Comparator<EnrichedSearchStore> comparator =
        switch (sort) {
          case RECOMMENDED ->
              Comparator.comparingDouble(
                      (EnrichedSearchStore e) ->
                          StoreQueryService.recommendedScore(
                              e.distanceKm(), e.rating(), e.activeDealCount()))
                  .reversed();
          case DISTANCE -> Comparator.comparingDouble(EnrichedSearchStore::distanceKm);
          case DISCOUNT ->
              (a, b) -> {
                boolean aHas = a.activeDealCount() > 0;
                boolean bHas = b.activeDealCount() > 0;
                if (aHas && bHas) return Double.compare(b.maxDiscountRate(), a.maxDiscountRate());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
          case CLOSING ->
              (a, b) -> {
                boolean aHas = a.nearestPickupEndAt() != null;
                boolean bHas = b.nearestPickupEndAt() != null;
                if (aHas && bHas) return a.nearestPickupEndAt().compareTo(b.nearestPickupEndAt());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
          case RATING ->
              (a, b) -> {
                boolean aHas = a.rating() > 0;
                boolean bHas = b.rating() > 0;
                if (aHas && bHas) return Double.compare(b.rating(), a.rating());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
        };
    return stores.stream().sorted(comparator).toList();
  }

  /** 상품 섹션 구성: deal + menu 합산 → sort. */
  private List<SearchProductItemResponse> buildProductSection(
      List<DealSearchCandidate> deals,
      List<ProductSearchCandidate> menus,
      Map<Long, String> storeNameMap,
      Map<Long, StoreCandidate> candidateMap,
      Map<Long, RatingStats> ratingMap,
      StoreSort sort) {

    // 상품 정렬에 쓰이는 매장 enrich 데이터
    // EnrichedProduct: deal/menu 아이템 + 매장 거리/평점/활성떨이카운트/할인율/마감시각 (정렬용, 응답 X)
    List<EnrichedProduct> enrichedDeals =
        deals.stream()
            .map(
                d -> {
                  StoreCandidate sc = candidateMap.get(d.getStoreId());
                  double distKm = sc != null ? sc.getDistanceMeters() / 1000.0 : 0.0;
                  RatingStats rs = ratingMap.getOrDefault(d.getStoreId(), RatingStats.EMPTY);
                  int discountRate = calcDiscountRate(d.getRegularPrice(), d.getSalePrice());
                  String storeName = storeNameMap.getOrDefault(d.getStoreId(), "");
                  DealSearchItem item =
                      new DealSearchItem(
                          d.getId(),
                          d.getStoreId(),
                          storeName,
                          d.getName(),
                          d.getImageUrl(),
                          d.getRegularPrice(),
                          d.getSalePrice(),
                          discountRate);
                  return new EnrichedProduct(
                      item, distKm, rs.average(), true, (double) discountRate, d.getPickupEndAt());
                })
            .toList();

    List<EnrichedProduct> enrichedMenus =
        menus.stream()
            .map(
                m -> {
                  StoreCandidate sc = candidateMap.get(m.getStoreId());
                  double distKm = sc != null ? sc.getDistanceMeters() / 1000.0 : 0.0;
                  RatingStats rs = ratingMap.getOrDefault(m.getStoreId(), RatingStats.EMPTY);
                  String storeName = storeNameMap.getOrDefault(m.getStoreId(), "");
                  MenuSearchItem item =
                      new MenuSearchItem(
                          m.getId(),
                          m.getStoreId(),
                          storeName,
                          m.getName(),
                          m.getImageUrl(),
                          m.getRegularPrice());
                  return new EnrichedProduct(item, distKm, rs.average(), false, 0.0, null);
                })
            .toList();

    List<EnrichedProduct> all = new ArrayList<>();
    all.addAll(enrichedDeals);
    all.addAll(enrichedMenus);

    return sortProducts(all, sort).stream().map(EnrichedProduct::item).toList();
  }

  private List<EnrichedProduct> sortProducts(List<EnrichedProduct> products, StoreSort sort) {
    Comparator<EnrichedProduct> comparator =
        switch (sort) {
          case RECOMMENDED ->
              Comparator.comparingDouble(
                      (EnrichedProduct p) ->
                          StoreQueryService.recommendedScore(
                              p.storeDistanceKm(), p.storeRating(), p.isDeal() ? 1L : 0L))
                  .reversed();
          case DISTANCE -> Comparator.comparingDouble(EnrichedProduct::storeDistanceKm);
          case DISCOUNT ->
              (a, b) -> {
                if (a.isDeal() && b.isDeal()) {
                  return Double.compare(b.discountRate(), a.discountRate());
                }
                if (a.isDeal()) return -1;
                if (b.isDeal()) return 1;
                return 0;
              };
          case CLOSING ->
              (a, b) -> {
                boolean aHas = a.isDeal() && a.pickupEndAt() != null;
                boolean bHas = b.isDeal() && b.pickupEndAt() != null;
                if (aHas && bHas) return a.pickupEndAt().compareTo(b.pickupEndAt());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
          case RATING ->
              (a, b) -> {
                boolean aHas = a.storeRating() > 0;
                boolean bHas = b.storeRating() > 0;
                if (aHas && bHas) return Double.compare(b.storeRating(), a.storeRating());
                if (aHas) return -1;
                if (bHas) return 1;
                return 0;
              };
        };
    return products.stream().sorted(comparator).toList();
  }

  /**
   * 할인율(%) 계산. round((1 - salePrice/regularPrice) * 100).
   *
   * @param regular 정상가
   * @param sale 판매가
   * @return 정수 할인율 (예: 40)
   */
  private int calcDiscountRate(BigDecimal regular, BigDecimal sale) {
    if (regular == null || sale == null || regular.compareTo(BigDecimal.ZERO) == 0) {
      return 0;
    }
    return BigDecimal.ONE
        .subtract(sale.divide(regular, 4, RoundingMode.HALF_UP))
        .multiply(BigDecimal.valueOf(100))
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  // ── 내부 데이터 홀더 ──────────────────────────────────────────────────────────────────────────

  private record EnrichedSearchStore(
      long id,
      String name,
      String imageUrl,
      double distanceKm,
      double rating,
      long activeDealCount,
      double maxDiscountRate,
      LocalDateTime nearestPickupEndAt,
      boolean isFavorite) {

    StoreListItemResponse toResponse() {
      return new StoreListItemResponse(
          id, name, imageUrl, distanceKm, rating, activeDealCount, isFavorite);
    }
  }

  private record EnrichedProduct(
      SearchProductItemResponse item,
      double storeDistanceKm,
      double storeRating,
      boolean isDeal,
      double discountRate,
      LocalDateTime pickupEndAt) {}

  private record ScoredSuggestion(String displayName, double similarity, SuggestionKind kind) {}
}
