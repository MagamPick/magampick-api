package com.magampick.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.magampick.address.domain.Address;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.clearance.repository.DealNameSuggestion;
import com.magampick.clearance.repository.DealSearchCandidate;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.product.repository.ProductNameSuggestion;
import com.magampick.product.repository.ProductRepository;
import com.magampick.product.repository.ProductSearchCandidate;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.search.dto.SearchProductItemResponse.DealSearchItem;
import com.magampick.search.dto.SearchProductItemResponse.MenuSearchItem;
import com.magampick.search.dto.SearchResultResponse;
import com.magampick.search.dto.SearchSuggestionResponse;
import com.magampick.search.dto.SuggestionKind;
import com.magampick.store.dto.StoreSort;
import com.magampick.store.repository.StoreCandidate;
import com.magampick.store.repository.StoreNameSuggestion;
import com.magampick.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchQueryServiceTest {

  @Mock AddressRepository addressRepository;
  @Mock StoreRepository storeRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock ProductRepository productRepository;
  @Mock FavoriteRepository favoriteRepository;
  @Mock ReviewQueryService reviewQueryService;
  @InjectMocks SearchQueryService searchQueryService;

  private static final Long CUSTOMER_ID = 1L;
  private static final double LAT = 37.5665;
  private static final double LNG = 126.9780;

  // ── 기본 주소지 ───────────────────────────────────────────────────────────────────────────────

  @Test
  void 기본주소지_없으면_예외() {
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> searchQueryService.search(CUSTOMER_ID, "빵집", StoreSort.RECOMMENDED))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 빈 키워드 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 빈_키워드는_빈_결과_반환() {
    SearchResultResponse result = searchQueryService.search(CUSTOMER_ID, "", StoreSort.RECOMMENDED);

    assertThat(result.stores()).isEmpty();
    assertThat(result.products()).isEmpty();
    verifyNoInteractions(addressRepository);
  }

  @Test
  void 공백만_있는_키워드는_빈_결과_반환() {
    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "   ", StoreSort.RECOMMENDED);

    assertThat(result.stores()).isEmpty();
    assertThat(result.products()).isEmpty();
    verifyNoInteractions(addressRepository);
  }

  // ── 와일드카드 이스케이프 ───────────────────────────────────────────────────────────────────────

  @Test
  void 검색_와일드카드_리터럴_이스케이프() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    stubEnrich(List.of(1L));

    ArgumentCaptor<String> storeQCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> dealQCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> productQCaptor = ArgumentCaptor.forClass(String.class);

    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), storeQCaptor.capture()))
        .willReturn(List.of());
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), dealQCaptor.capture()))
        .willReturn(List.of());
    given(productRepository.searchOnSaleProductsByStoreIds(any(), productQCaptor.capture()))
        .willReturn(List.of());

    searchQueryService.search(CUSTOMER_ID, "%", StoreSort.RECOMMENDED);

    // 입력 "%" → escaped "\\%" 가 레포지토리에 전달돼야 함 (리터럴 % 매칭)
    assertThat(storeQCaptor.getValue()).isEqualTo("\\%");
    assertThat(dealQCaptor.getValue()).isEqualTo("\\%");
    assertThat(productQCaptor.getValue()).isEqualTo("\\%");
  }

  // ── 매장 섹션 검색 ───────────────────────────────────────────────────────────────────────────

  @Test
  void 매장명_일치_매장만_stores_섹션에_포함() {
    stubDefaultAddress();
    // 매장명 일치: store1
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), eq("빵집")))
        .willReturn(List.of(1L));
    // 전체 후보: store1 + store2
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "빵집", 1000.0), storeCandidate(2L, "카페", 500.0)));
    stubEnrich(List.of(1L, 2L));
    // 떨이 검색 (store IDs 기준): 없음
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), eq("빵집"))).willReturn(List.of());
    // 메뉴 검색: 없음
    given(productRepository.searchOnSaleProductsByStoreIds(any(), eq("빵집"))).willReturn(List.of());

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "빵집", StoreSort.RECOMMENDED);

    // stores 섹션: 매장명 일치한 store1 만
    assertThat(result.stores()).hasSize(1);
    assertThat(result.stores().get(0).id()).isEqualTo(1L);
  }

  // ── 상품 섹션 검색 ───────────────────────────────────────────────────────────────────────────

  @Test
  void 떨이_이름_일치_products_섹션에_deal_kind로_포함() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of()); // 매장 이름 불일치
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 1000.0)));
    stubEnrich(List.of(1L));
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(List.of(dealCandidate(10L, 1L, "크로아상", "5000", "3500")));
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of());

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "크로아상", StoreSort.RECOMMENDED);

    assertThat(result.products()).hasSize(1);
    assertThat(result.products().get(0)).isInstanceOf(DealSearchItem.class);
    DealSearchItem deal = (DealSearchItem) result.products().get(0);
    assertThat(deal.kind()).isEqualTo("deal");
    assertThat(deal.id()).isEqualTo(10L);
    assertThat(deal.storeId()).isEqualTo(1L);
    assertThat(deal.name()).isEqualTo("크로아상");
  }

  @Test
  void 메뉴_이름_일치_products_섹션에_menu_kind로_포함() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 1000.0)));
    stubEnrich(List.of(1L));
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(List.of());
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of(productCandidate(20L, 1L, "아메리카노", "4500")));

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "아메리카노", StoreSort.RECOMMENDED);

    assertThat(result.products()).hasSize(1);
    assertThat(result.products().get(0)).isInstanceOf(MenuSearchItem.class);
    MenuSearchItem menu = (MenuSearchItem) result.products().get(0);
    assertThat(menu.kind()).isEqualTo("menu");
    assertThat(menu.id()).isEqualTo(20L);
    assertThat(menu.price()).isEqualByComparingTo(new BigDecimal("4500"));
  }

  @Test
  void 떨이와_메뉴_동시_일치시_둘다_포함() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 1000.0)));
    stubEnrich(List.of(1L));
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(List.of(dealCandidate(10L, 1L, "라떼할인", "5000", "3500")));
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of(productCandidate(20L, 1L, "라떼", "5000")));

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "라떼", StoreSort.RECOMMENDED);

    assertThat(result.products()).hasSize(2);
    assertThat(result.products().stream().filter(p -> p instanceof DealSearchItem)).hasSize(1);
    assertThat(result.products().stream().filter(p -> p instanceof MenuSearchItem)).hasSize(1);
  }

  // ── 정렬: DISCOUNT / CLOSING — menu 는 LAST ────────────────────────────────────────────────

  @Test
  void DISCOUNT_정렬_메뉴는_마지막에() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    stubEnrich(List.of(1L));
    // deal: 할인율 30%
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(
            List.of(
                dealCandidateWithPickup(
                    10L, 1L, "크로아상", "5000", "3500", LocalDateTime.now().plusHours(2))));
    // menu: 할인 없음
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of(productCandidate(20L, 1L, "아메리카노", "4500")));

    SearchResultResponse result = searchQueryService.search(CUSTOMER_ID, "카페", StoreSort.DISCOUNT);

    assertThat(result.products()).hasSize(2);
    // deal 이 앞에, menu 가 뒤에
    assertThat(result.products().get(0)).isInstanceOf(DealSearchItem.class);
    assertThat(result.products().get(1)).isInstanceOf(MenuSearchItem.class);
  }

  @Test
  void CLOSING_정렬_메뉴는_마지막에() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    stubEnrich(List.of(1L));
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(
            List.of(
                dealCandidateWithPickup(
                    10L, 1L, "크로아상", "5000", "3500", LocalDateTime.now().plusHours(1))));
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of(productCandidate(20L, 1L, "아메리카노", "4500")));

    SearchResultResponse result = searchQueryService.search(CUSTOMER_ID, "카페", StoreSort.CLOSING);

    assertThat(result.products().get(0)).isInstanceOf(DealSearchItem.class);
    assertThat(result.products().get(1)).isInstanceOf(MenuSearchItem.class);
  }

  @Test
  void 정렬_RECOMMENDED_deal은_bonus_적용() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    given(reviewQueryService.getStoreRatings(any()))
        .willReturn(Map.of(1L, new RatingStats(4.0, 5L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());
    // deal: 1개
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(
            List.of(
                dealCandidateWithPickup(
                    10L, 1L, "크로아상", "5000", "3500", LocalDateTime.now().plusHours(2))));
    // menu: 1개
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of(productCandidate(20L, 1L, "라떼", "4500")));

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "카페", StoreSort.RECOMMENDED);

    // 두 항목 모두 포함 — 같은 매장(거리·평점 동일), deal 보너스(+1.5) 로 deal 이 menu 보다 앞에 와야 함
    assertThat(result.products()).hasSize(2);
    assertThat(result.products().get(0)).isInstanceOf(DealSearchItem.class);
    assertThat(((DealSearchItem) result.products().get(0)).id()).isEqualTo(10L);
    assertThat(result.products().get(1)).isInstanceOf(MenuSearchItem.class);
    assertThat(((MenuSearchItem) result.products().get(1)).id()).isEqualTo(20L);
  }

  // ── 할인율 계산 ──────────────────────────────────────────────────────────────────────────────

  @Test
  void 할인율_정수_계산_정확() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    stubEnrich(List.of(1L));
    // regular=5000, sale=3000 → discountRate = round((1 - 3000/5000) * 100) = 40
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(List.of(dealCandidate(10L, 1L, "크로아상", "5000", "3000")));
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of());

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "크로아상", StoreSort.RECOMMENDED);

    DealSearchItem deal = (DealSearchItem) result.products().get(0);
    assertThat(deal.discountRate()).isEqualTo(40);
  }

  // ── storeName 매핑 ────────────────────────────────────────────────────────────────────────

  @Test
  void 상품_응답에_storeName_포함됨() {
    stubDefaultAddress();
    given(
            storeRepository.findStoreIdsWithin5kmMatchingName(
                anyDouble(), anyDouble(), anyString(), anyString()))
        .willReturn(List.of());
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "우리빵집", 500.0)));
    stubEnrich(List.of(1L));
    given(clearanceItemRepository.searchOpenDealsByStoreIds(any(), anyString()))
        .willReturn(List.of(dealCandidate(10L, 1L, "크로아상", "5000", "3500")));
    given(productRepository.searchOnSaleProductsByStoreIds(any(), anyString()))
        .willReturn(List.of());

    SearchResultResponse result =
        searchQueryService.search(CUSTOMER_ID, "크로아상", StoreSort.RECOMMENDED);

    DealSearchItem deal = (DealSearchItem) result.products().get(0);
    assertThat(deal.storeName()).isEqualTo("우리빵집");
  }

  // ── 자동완성 ──────────────────────────────────────────────────────────────────────────────────

  @Test
  void 자동완성_1자_미만_빈_결과() {
    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, "");
    assertThat(result).isEmpty();
    verifyNoInteractions(addressRepository);
  }

  @Test
  void 자동완성_공백만_있으면_빈_결과() {
    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, " ");
    assertThat(result).isEmpty();
    verifyNoInteractions(addressRepository);
  }

  @Test
  void 자동완성_매장명_제안_store_kind() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "빵집", 500.0)));
    given(
            storeRepository.suggestStoreNamesWithin5km(
                anyDouble(), anyDouble(), anyString(), anyString(), anyDouble()))
        .willReturn(List.of(storeSuggestion("빵집", 0.8)));
    given(clearanceItemRepository.suggestDealNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of());
    given(productRepository.suggestProductNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of());

    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, "빵");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).kind()).isEqualTo(SuggestionKind.STORE);
    assertThat(result.get(0).text()).isEqualTo("빵집");
  }

  @Test
  void 자동완성_상품명_제안_product_kind() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    given(
            storeRepository.suggestStoreNamesWithin5km(
                anyDouble(), anyDouble(), anyString(), anyString(), anyDouble()))
        .willReturn(List.of());
    given(clearanceItemRepository.suggestDealNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of(dealSuggestion("크로아상", 0.7)));
    given(productRepository.suggestProductNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of());

    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, "크로");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).kind()).isEqualTo(SuggestionKind.PRODUCT);
    assertThat(result.get(0).text()).isEqualTo("크로아상");
  }

  @Test
  void 자동완성_중복_텍스트_dedup() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    given(
            storeRepository.suggestStoreNamesWithin5km(
                anyDouble(), anyDouble(), anyString(), anyString(), anyDouble()))
        .willReturn(List.of());
    // 동일 이름이 deal, menu 두 곳에서 나옴
    given(clearanceItemRepository.suggestDealNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of(dealSuggestion("크로아상", 0.8)));
    given(productRepository.suggestProductNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of(productSuggestion("크로아상", 0.7)));

    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, "크로");

    // 같은 텍스트 → dedup → 1개
    assertThat(result).hasSize(1);
    assertThat(result.get(0).text()).isEqualTo("크로아상");
  }

  @Test
  void 자동완성_최대_10개() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    given(
            storeRepository.suggestStoreNamesWithin5km(
                anyDouble(), anyDouble(), anyString(), anyString(), anyDouble()))
        .willReturn(List.of());
    // 15개 반환
    List<DealNameSuggestion> manyDeals = new java.util.ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      final int fi = i;
      manyDeals.add(dealSuggestion("크로아상" + i, 0.9 - fi * 0.01));
    }
    given(clearanceItemRepository.suggestDealNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(manyDeals);
    given(productRepository.suggestProductNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of());

    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, "크로");

    assertThat(result).hasSize(10);
  }

  @Test
  void 자동완성_유사도_내림차순_정렬() {
    stubDefaultAddress();
    given(storeRepository.findOpenStoresWithin5km(anyDouble(), anyDouble(), anyString()))
        .willReturn(List.of(storeCandidate(1L, "카페", 500.0)));
    given(
            storeRepository.suggestStoreNamesWithin5km(
                anyDouble(), anyDouble(), anyString(), anyString(), anyDouble()))
        .willReturn(List.of(storeSuggestion("카페오", 0.5)));
    given(clearanceItemRepository.suggestDealNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of(dealSuggestion("카페라떼", 0.9)));
    given(productRepository.suggestProductNamesByStoreIds(anyList(), anyString(), anyDouble()))
        .willReturn(List.of(productSuggestion("카페아메리카노", 0.7)));

    List<SearchSuggestionResponse> result = searchQueryService.autocomplete(CUSTOMER_ID, "카페");

    // 유사도 내림차순: 카페라떼(0.9) → 카페아메리카노(0.7) → 카페오(0.5)
    assertThat(result.get(0).text()).isEqualTo("카페라떼");
    assertThat(result.get(1).text()).isEqualTo("카페아메리카노");
    assertThat(result.get(2).text()).isEqualTo("카페오");
  }

  @Test
  void 자동완성_기본주소지_없으면_예외() {
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> searchQueryService.autocomplete(CUSTOMER_ID, "빵"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── helper ───────────────────────────────────────────────────────────────────────────────────

  private void stubDefaultAddress() {
    Address address =
        Address.builder()
            .customer(null)
            .label("집")
            .roadAddress("서울시 중구 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(LAT, LNG))
            .isDefault(true)
            .build();
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(address));
  }

  private void stubEnrich(List<Long> storeIds) {
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                any(), eq(ClearanceItemStatus.OPEN)))
        .willReturn(List.of());
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(eq(CUSTOMER_ID), any()))
        .willReturn(List.of());
  }

  private StoreCandidate storeCandidate(Long id, String name, double distanceMeters) {
    return new StoreCandidate() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getImageUrl() {
        return null;
      }

      @Override
      public Double getDistanceMeters() {
        return distanceMeters;
      }
    };
  }

  private DealSearchCandidate dealCandidate(
      Long id, Long storeId, String name, String regular, String sale) {
    return dealCandidateWithPickup(
        id, storeId, name, regular, sale, LocalDateTime.now().plusHours(2));
  }

  private DealSearchCandidate dealCandidateWithPickup(
      Long id, Long storeId, String name, String regular, String sale, LocalDateTime pickupEndAt) {
    return new DealSearchCandidate() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public Long getStoreId() {
        return storeId;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getImageUrl() {
        return null;
      }

      @Override
      public BigDecimal getRegularPrice() {
        return new BigDecimal(regular);
      }

      @Override
      public BigDecimal getSalePrice() {
        return new BigDecimal(sale);
      }

      @Override
      public LocalDateTime getPickupEndAt() {
        return pickupEndAt;
      }
    };
  }

  private ProductSearchCandidate productCandidate(
      Long id, Long storeId, String name, String price) {
    return new ProductSearchCandidate() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public Long getStoreId() {
        return storeId;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getImageUrl() {
        return null;
      }

      @Override
      public BigDecimal getRegularPrice() {
        return new BigDecimal(price);
      }
    };
  }

  private StoreNameSuggestion storeSuggestion(String name, double similarity) {
    return new StoreNameSuggestion() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Double getSimilarity() {
        return similarity;
      }
    };
  }

  private DealNameSuggestion dealSuggestion(String name, double similarity) {
    return new DealNameSuggestion() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Double getSimilarity() {
        return similarity;
      }
    };
  }

  private ProductNameSuggestion productSuggestion(String name, double similarity) {
    return new ProductNameSuggestion() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Double getSimilarity() {
        return similarity;
      }
    };
  }
}
