package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.magampick.address.domain.Address;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.repository.AddressRepository;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.dto.ConsumerStoreDetailResponse;
import com.magampick.store.dto.OperatingHourResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreDetailQueryServiceTest {

  @Mock StoreRepository storeRepository;
  @Mock StoreBusinessHourRepository storeBusinessHourRepository;
  @Mock ReviewQueryService reviewQueryService;
  @Mock AddressRepository addressRepository;
  @Mock FavoriteRepository favoriteRepository;
  @InjectMocks StoreDetailQueryService service;

  private static final Long STORE_ID = 10L;
  private static final Long CUSTOMER_ID = 1L;
  private static final double ORIGIN_LAT = 37.5665;
  private static final double ORIGIN_LNG = 126.9780;

  // ── 매장 없음 ─────────────────────────────────────────────────────────────────────────────────

  @Test
  void 매장_없으면_STORE_NOT_FOUND_예외() {
    given(storeRepository.findByIdAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail(STORE_ID, CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(StoreErrorCode.STORE_NOT_FOUND);
  }

  // ── 기본 주소지 없음 ─────────────────────────────────────────────────────────────────────────────

  @Test
  void 기본_주소지_없으면_DEFAULT_ADDRESS_REQUIRED_예외() {
    given(storeRepository.findByIdAndDeletedAtIsNull(STORE_ID))
        .willReturn(Optional.of(stubStore(OperationStatus.OPEN)));
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDetail(STORE_ID, CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  // ── 정상 조립 ────────────────────────────────────────────────────────────────────────────────

  @Test
  void 정상_조회_평점_거리_isFavorite_businessStatus_포함() {
    Store store = stubStore(OperationStatus.OPEN);
    given(storeRepository.findByIdAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(store));
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(stubAddress()));
    given(reviewQueryService.getStoreRating(STORE_ID)).willReturn(new RatingStats(4.2, 8L));
    given(storeRepository.findDistanceMeters(anyLong(), anyDouble(), anyDouble()))
        .willReturn(1500.0);
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of(STORE_ID));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    ConsumerStoreDetailResponse response = service.getDetail(STORE_ID, CUSTOMER_ID);

    assertThat(response.id()).isEqualTo(STORE_ID);
    assertThat(response.rating()).isEqualTo(4.2);
    assertThat(response.reviewCount()).isEqualTo(8L);
    assertThat(response.distanceKm()).isEqualTo(1.5); // 1500m → 1.5km
    assertThat(response.isFavorite()).isTrue();
    assertThat(response.businessStatus()).isEqualTo(OperationStatus.OPEN);
  }

  // ── operatingHours: 7요일, 없는 요일은 closed=true ─────────────────────────────────────────────

  @Test
  void 영업시간_7요일_매핑_없는_요일_closed() {
    Store store = stubStore(OperationStatus.OPEN);
    given(storeRepository.findByIdAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(store));
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(stubAddress()));
    given(reviewQueryService.getStoreRating(STORE_ID)).willReturn(RatingStats.EMPTY);
    given(storeRepository.findDistanceMeters(anyLong(), anyDouble(), anyDouble()))
        .willReturn(500.0);
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());

    // 월/수/금만 영업
    StoreBusinessHour monday = stubHour(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(20, 0));
    StoreBusinessHour wed = stubHour(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(22, 0));
    StoreBusinessHour fri = stubHour(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(18, 0));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID))
        .willReturn(List.of(monday, wed, fri));

    ConsumerStoreDetailResponse response = service.getDetail(STORE_ID, CUSTOMER_ID);

    List<OperatingHourResponse> hours = response.operatingHours();
    assertThat(hours).hasSize(7);
    // 월
    assertThat(hours.get(0).day()).isEqualTo("월");
    assertThat(hours.get(0).closed()).isFalse();
    assertThat(hours.get(0).open()).isEqualTo("09:00");
    assertThat(hours.get(0).close()).isEqualTo("20:00");
    // 화 (없는 요일)
    assertThat(hours.get(1).day()).isEqualTo("화");
    assertThat(hours.get(1).closed()).isTrue();
    assertThat(hours.get(1).open()).isNull();
    // 수
    assertThat(hours.get(2).day()).isEqualTo("수");
    assertThat(hours.get(2).closed()).isFalse();
    // 목 (없는 요일)
    assertThat(hours.get(3).closed()).isTrue();
    // 금
    assertThat(hours.get(4).closed()).isFalse();
    // 토, 일 (없는 요일)
    assertThat(hours.get(5).closed()).isTrue();
    assertThat(hours.get(6).closed()).isTrue();
    // 일 라벨
    assertThat(hours.get(6).day()).isEqualTo("일");
  }

  // ── closingTime: 오늘 영업 요일이면 마감시각 반환, 휴무이면 null ─────────────────────────────────────────

  @Test
  void 오늘_휴무이면_closingTime_null() {
    Store store = stubStore(OperationStatus.CLOSED_TODAY);
    given(storeRepository.findByIdAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(store));
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(stubAddress()));
    given(reviewQueryService.getStoreRating(STORE_ID)).willReturn(RatingStats.EMPTY);
    given(storeRepository.findDistanceMeters(anyLong(), anyDouble(), anyDouble()))
        .willReturn(200.0);
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of());
    // 오늘 요일에 해당하는 영업시간 row 없음
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    ConsumerStoreDetailResponse response = service.getDetail(STORE_ID, CUSTOMER_ID);

    assertThat(response.closingTime()).isNull();
  }

  // ── isFavorite: 단골 아니면 false ─────────────────────────────────────────────────────────────

  @Test
  void 단골_아니면_isFavorite_false() {
    Store store = stubStore(OperationStatus.OPEN);
    given(storeRepository.findByIdAndDeletedAtIsNull(STORE_ID)).willReturn(Optional.of(store));
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(stubAddress()));
    given(reviewQueryService.getStoreRating(STORE_ID)).willReturn(RatingStats.EMPTY);
    given(storeRepository.findDistanceMeters(anyLong(), anyDouble(), anyDouble()))
        .willReturn(200.0);
    given(favoriteRepository.findStoreIdsByCustomerIdAndStoreIdIn(any(), any()))
        .willReturn(List.of()); // 단골 없음
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    ConsumerStoreDetailResponse response = service.getDetail(STORE_ID, CUSTOMER_ID);

    assertThat(response.isFavorite()).isFalse();
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────────────

  /** Reflection 으로 id/location 주입. Builder 는 private 생성자. */
  private Store stubStore(OperationStatus status) {
    Store store =
        Store.builder()
            .seller(stubSeller())
            .businessNumber("1234567890")
            .name("테스트매장")
            .roadAddress("서울시 중구 테스트로 1")
            .zonecode("04524")
            .location(GeometryUtil.toPoint(37.5685, 126.9800))
            .phone("02-1234-5678")
            .operationStatus(status)
            .build();
    setId(store, STORE_ID);
    return store;
  }

  private Seller stubSeller() {
    return Seller.builder().email("seller@test.com").passwordHash("x").ownerName("사장님").build();
  }

  private Address stubAddress() {
    return Address.builder()
        .customer(null)
        .label("집")
        .roadAddress("서울시 중구 테스트로 1")
        .zonecode("04524")
        .location(GeometryUtil.toPoint(ORIGIN_LAT, ORIGIN_LNG))
        .isDefault(true)
        .build();
  }

  private StoreBusinessHour stubHour(DayOfWeek day, LocalTime open, LocalTime close) {
    Store store = stubStore(OperationStatus.OPEN);
    return StoreBusinessHour.builder()
        .store(store)
        .dayOfWeek(day)
        .openTime(open)
        .closeTime(close)
        .build();
  }

  /** Reflection 으로 private id 필드 주입. */
  private void setId(Object entity, Long id) {
    try {
      Field field = getField(entity.getClass(), "id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (Exception e) {
      throw new RuntimeException("id 주입 실패", e);
    }
  }

  private Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException e) {
      if (clazz.getSuperclass() != null) {
        return getField(clazz.getSuperclass(), name);
      }
      throw e;
    }
  }
}
