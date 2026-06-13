package com.magampick.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.service.AddressService;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteListResponse;
import com.magampick.favorite.exception.FavoriteErrorCode;
import com.magampick.favorite.fixture.FavoriteFixture;
import com.magampick.favorite.mapper.FavoriteMapper;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.favorite.repository.FavoriteStoreCandidate;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

  @Mock FavoriteRepository favoriteRepository;
  @Mock StoreRepository storeRepository;
  @Mock FavoriteMapper favoriteMapper;
  @Mock FavoriteInserter favoriteInserter;
  @Mock AddressService addressService;
  @Mock ReviewQueryService reviewQueryService;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @InjectMocks FavoriteService favoriteService;

  private static final Long CUSTOMER_ID = 1L;
  private static final Long STORE_ID = 10L;
  private static final Long STORE_ID_A = 10L;
  private static final Long STORE_ID_B = 20L;

  // ── 즐겨찾기 등록 ────────────────────────────────────────────────────────────

  @Test
  void 즐겨찾기_등록_성공() {
    // given
    given(storeRepository.existsById(STORE_ID)).willReturn(true);
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.empty());
    given(favoriteRepository.countByCustomerId(CUSTOMER_ID)).willReturn(5L);
    FavoriteAddResponse expected = FavoriteFixture.aAddResponse(STORE_ID);
    given(favoriteInserter.insert(CUSTOMER_ID, STORE_ID)).willReturn(expected);

    // when
    FavoriteAddResponse response = favoriteService.addFavorite(CUSTOMER_ID, STORE_ID);

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    then(favoriteInserter).should().insert(CUSTOMER_ID, STORE_ID);
  }

  @Test
  void 이미_즐겨찾기된_경우_멱등_처리() {
    // given
    Store store = store(STORE_ID);
    Customer customer = customer();
    Favorite existing = FavoriteFixture.aFavorite(customer, store);
    given(storeRepository.existsById(STORE_ID)).willReturn(true);
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.of(existing));
    given(favoriteMapper.toAddResponse(existing))
        .willReturn(FavoriteFixture.aAddResponse(STORE_ID));

    // when
    FavoriteAddResponse response = favoriteService.addFavorite(CUSTOMER_ID, STORE_ID);

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    then(favoriteInserter).should(never()).insert(any(), any());
  }

  @Test
  void 존재하지_않는_매장_즐겨찾기_등록_실패_STORE_NOT_FOUND() {
    // given
    given(storeRepository.existsById(STORE_ID)).willReturn(false);

    // when / then
    assertThatThrownBy(() -> favoriteService.addFavorite(CUSTOMER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_NOT_FOUND);
    then(favoriteInserter).should(never()).insert(any(), any());
  }

  @Test
  void 단골_50개_초과_등록_실패_FAVORITE_LIMIT_REACHED() {
    // given — 이미 50개(상한) 등록된 상태에서 신규 매장 추가 시도
    given(storeRepository.existsById(STORE_ID)).willReturn(true);
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.empty());
    given(favoriteRepository.countByCustomerId(CUSTOMER_ID)).willReturn(50L);

    // when / then
    assertThatThrownBy(() -> favoriteService.addFavorite(CUSTOMER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", FavoriteErrorCode.FAVORITE_LIMIT_REACHED);
    then(favoriteInserter).should(never()).insert(any(), any());
  }

  @Test
  void 한도_도달해도_이미_단골이면_재추가_멱등_성공() {
    // given — 50개 한도 상태라도 이미 단골인 매장 재추가는 한도 체크를 건너뛰고 성공해야 함
    Store store = store(STORE_ID);
    Customer customer = customer();
    Favorite existing = FavoriteFixture.aFavorite(customer, store);
    given(storeRepository.existsById(STORE_ID)).willReturn(true);
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.of(existing));
    given(favoriteMapper.toAddResponse(existing))
        .willReturn(FavoriteFixture.aAddResponse(STORE_ID));

    // when
    FavoriteAddResponse response = favoriteService.addFavorite(CUSTOMER_ID, STORE_ID);

    // then — 한도 체크(count)·등록(insert) 모두 건너뜀
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    then(favoriteRepository).should(never()).countByCustomerId(any());
    then(favoriteInserter).should(never()).insert(any(), any());
  }

  @Test
  void 동시_추가_레이스_unique_위반시_멱등_성공() {
    // given — 사전 중복체크는 통과(empty)했으나 INSERT 시 다른 트랜잭션이 선점해 unique 위반
    Store store = store(STORE_ID);
    Customer customer = customer();
    Favorite winner = FavoriteFixture.aFavorite(customer, store);
    given(storeRepository.existsById(STORE_ID)).willReturn(true);
    given(favoriteRepository.findByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID))
        .willReturn(Optional.empty(), Optional.of(winner)); // 사전체크 empty → 위반 후 재조회 present
    given(favoriteRepository.countByCustomerId(CUSTOMER_ID)).willReturn(3L);
    given(favoriteInserter.insert(CUSTOMER_ID, STORE_ID))
        .willThrow(new DataIntegrityViolationException("uk_favorites_customer_store"));
    given(favoriteMapper.toAddResponse(winner)).willReturn(FavoriteFixture.aAddResponse(STORE_ID));

    // when
    FavoriteAddResponse response = favoriteService.addFavorite(CUSTOMER_ID, STORE_ID);

    // then — 500 이 아니라 멱등 성공으로 수렴
    assertThat(response.storeId()).isEqualTo(STORE_ID);
  }

  // ── 즐겨찾기 해제 ────────────────────────────────────────────────────────────

  @Test
  void 즐겨찾기_해제_성공() {
    // when
    favoriteService.removeFavorite(CUSTOMER_ID, STORE_ID);

    // then
    then(favoriteRepository).should().deleteByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID);
  }

  @Test
  void 미등록_매장_해제_멱등_처리() {
    // when
    favoriteService.removeFavorite(CUSTOMER_ID, STORE_ID);

    // then — 예외 없이 정상 완료
    then(favoriteRepository).should().deleteByCustomerIdAndStoreId(CUSTOMER_ID, STORE_ID);
  }

  // ── 단골 목록 조회 ──────────────────────────────────────────────────────────

  @Test
  void 단골_없으면_빈_응답() {
    // given
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(37.5665, 126.9780));
    given(
            favoriteRepository.findFavoriteStoresWithDistance(
                eq(CUSTOMER_ID), anyDouble(), anyDouble()))
        .willReturn(List.of());

    // when
    FavoriteListResponse response = favoriteService.getFavorites(CUSTOMER_ID);

    // then
    assertThat(response.stores()).isEmpty();
    assertThat(response.totalCount()).isZero();
    assertThat(response.totalActiveDealCount()).isZero();
  }

  @Test
  void 기본주소지_없으면_DEFAULT_ADDRESS_REQUIRED() {
    // given
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willThrow(new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    // when / then
    assertThatThrownBy(() -> favoriteService.getFavorites(CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  @Test
  void 단골_목록_distanceKm_rating_activeDealCount_enrich() {
    // given
    double distMeters = 1500.0;
    FavoriteStoreCandidate cand =
        mockCandidate(STORE_ID, "동네빵집", "/img.jpg", distMeters, LocalDateTime.now());
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(37.5665, 126.9780));
    given(
            favoriteRepository.findFavoriteStoresWithDistance(
                eq(CUSTOMER_ID), anyDouble(), anyDouble()))
        .willReturn(List.of(cand));
    given(reviewQueryService.getStoreRatings(List.of(STORE_ID)))
        .willReturn(Map.of(STORE_ID, new RatingStats(4.5, 10L)));
    given(
            clearanceItemRepository.findActiveDealSummaryByStoreIds(
                List.of(STORE_ID), ClearanceItemStatus.OPEN))
        .willReturn(dealRows(new Object[] {STORE_ID, 3L, null, null}));

    // when
    FavoriteListResponse response = favoriteService.getFavorites(CUSTOMER_ID);

    // then
    assertThat(response.stores()).hasSize(1);
    var item = response.stores().get(0);
    assertThat(item.id()).isEqualTo(STORE_ID);
    assertThat(item.name()).isEqualTo("동네빵집");
    assertThat(item.imageUrl()).isEqualTo("/img.jpg");
    assertThat(item.distanceKm()).isEqualTo(1.5);
    assertThat(item.rating()).isEqualTo(4.5);
    assertThat(item.activeDealCount()).isEqualTo(3L);
  }

  @Test
  void 단골_목록_totalCount_totalActiveDealCount_통계() {
    // given
    FavoriteStoreCandidate candA =
        mockCandidate(STORE_ID_A, "매장A", null, 1000.0, LocalDateTime.now());
    FavoriteStoreCandidate candB =
        mockCandidate(STORE_ID_B, "매장B", null, 2000.0, LocalDateTime.now());
    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(37.5665, 126.9780));
    given(
            favoriteRepository.findFavoriteStoresWithDistance(
                eq(CUSTOMER_ID), anyDouble(), anyDouble()))
        .willReturn(List.of(candA, candB));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(clearanceItemRepository.findActiveDealSummaryByStoreIds(any(), any()))
        .willReturn(
            dealRows(
                new Object[] {STORE_ID_A, 2L, null, null},
                new Object[] {STORE_ID_B, 3L, null, null}));

    // when
    FavoriteListResponse response = favoriteService.getFavorites(CUSTOMER_ID);

    // then
    assertThat(response.totalCount()).isEqualTo(2L);
    assertThat(response.totalActiveDealCount()).isEqualTo(5L);
  }

  @Test
  void 단골_목록_떨이활성_우선_등록순_2차_정렬() {
    // storeB: 먼저 등록(older), 활성 떨이 없음
    // storeA: 나중 등록(newer), 활성 떨이 2개
    // 결과: storeA(활성떨이) → storeB(등록순 무관) 순서 기대
    LocalDateTime older = LocalDateTime.of(2024, 1, 1, 0, 0);
    LocalDateTime newer = LocalDateTime.of(2024, 1, 2, 0, 0);

    FavoriteStoreCandidate candB = mockCandidate(STORE_ID_B, "매장B", null, 1000.0, older); // 먼저 등록
    FavoriteStoreCandidate candA = mockCandidate(STORE_ID_A, "매장A", null, 2000.0, newer); // 나중 등록

    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(37.5665, 126.9780));
    given(
            favoriteRepository.findFavoriteStoresWithDistance(
                eq(CUSTOMER_ID), anyDouble(), anyDouble()))
        .willReturn(List.of(candB, candA)); // DB 반환 순서: B, A
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(clearanceItemRepository.findActiveDealSummaryByStoreIds(any(), any()))
        .willReturn(dealRows(new Object[] {STORE_ID_A, 2L, null, null})); // storeA 만 활성 떨이

    // when
    FavoriteListResponse response = favoriteService.getFavorites(CUSTOMER_ID);

    // then — storeA (활성떨이) 먼저, storeB 나중
    assertThat(response.stores()).hasSize(2);
    assertThat(response.stores().get(0).id()).isEqualTo(STORE_ID_A);
    assertThat(response.stores().get(1).id()).isEqualTo(STORE_ID_B);
  }

  @Test
  void 단골_목록_동률_등록순_asc_정렬() {
    // 두 매장 모두 활성 떨이 없음 → 등록순(createdAt asc)으로 정렬
    LocalDateTime first = LocalDateTime.of(2024, 1, 1, 0, 0);
    LocalDateTime second = LocalDateTime.of(2024, 1, 2, 0, 0);

    FavoriteStoreCandidate candA = mockCandidate(STORE_ID_A, "매장A", null, 1000.0, second); // 나중 등록
    FavoriteStoreCandidate candB = mockCandidate(STORE_ID_B, "매장B", null, 2000.0, first); // 먼저 등록

    given(addressService.requireDefaultLocation(CUSTOMER_ID))
        .willReturn(GeometryUtil.toPoint(37.5665, 126.9780));
    given(
            favoriteRepository.findFavoriteStoresWithDistance(
                eq(CUSTOMER_ID), anyDouble(), anyDouble()))
        .willReturn(List.of(candA, candB));
    given(reviewQueryService.getStoreRatings(any())).willReturn(Map.of());
    given(clearanceItemRepository.findActiveDealSummaryByStoreIds(any(), any()))
        .willReturn(List.of()); // 두 매장 모두 활성 떨이 없음

    // when
    FavoriteListResponse response = favoriteService.getFavorites(CUSTOMER_ID);

    // then — storeB (먼저 등록) 먼저, storeA (나중 등록) 나중
    assertThat(response.stores()).hasSize(2);
    assertThat(response.stores().get(0).id()).isEqualTo(STORE_ID_B);
    assertThat(response.stores().get(1).id()).isEqualTo(STORE_ID_A);
  }

  // ── private helpers ────────────────────────────────────────────────────────

  private Customer customer() {
    Customer c =
        Customer.builder()
            .email("test@example.com")
            .passwordHash("hash")
            .nickname("테스터")
            .phone("01012345678")
            .build();
    ReflectionTestUtils.setField(c, "id", CUSTOMER_ID);
    return c;
  }

  private Store store(Long id) {
    Store s =
        Store.builder()
            .seller(null)
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(null)
            .phone("0212345678")
            .imageUrl("/uploads/store.jpg")
            .build();
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }

  private FavoriteStoreCandidate mockCandidate(
      Long storeId, String name, String imageUrl, double distanceMeters, LocalDateTime createdAt) {
    // lenient: 일부 테스트에서 createdAt / imageUrl 이 호출 안 될 수 있어 strict 위반 방지
    FavoriteStoreCandidate candidate = mock(FavoriteStoreCandidate.class);
    lenient().when(candidate.getStoreId()).thenReturn(storeId);
    lenient().when(candidate.getName()).thenReturn(name);
    lenient().when(candidate.getImageUrl()).thenReturn(imageUrl);
    lenient().when(candidate.getDistanceMeters()).thenReturn(distanceMeters);
    lenient().when(candidate.getCreatedAt()).thenReturn(createdAt);
    return candidate;
  }

  /** Object[] varargs 타입 추론 문제 회피용 헬퍼. List<Object[]> 를 안전하게 생성한다. */
  private static List<Object[]> dealRows(Object[]... rows) {
    List<Object[]> list = new ArrayList<>(rows.length);
    for (Object[] row : rows) {
      list.add(row);
    }
    return list;
  }
}
