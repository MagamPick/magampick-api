package com.magampick.store.service;

import com.magampick.address.service.AddressService;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.review.service.RatingStats;
import com.magampick.review.service.ReviewQueryService;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.dto.ConsumerStoreDetailResponse;
import com.magampick.store.dto.OperatingHourResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 매장 상세 조회 서비스. store + 영업시간 + 평점 + 거리 + isFavorite 조립.
 *
 * <p>거리 origin = 기본 주소지 (없으면 DEFAULT_ADDRESS_REQUIRED 400).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreDetailQueryService {

  /** 요일 순서 월~일 (DayOfWeek.MONDAY=1 ~ SUNDAY=7). */
  private static final List<DayOfWeek> DAY_ORDER =
      List.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY,
          DayOfWeek.SATURDAY,
          DayOfWeek.SUNDAY);

  /** 요일 한국어 라벨. */
  private static final Map<DayOfWeek, String> DAY_LABEL =
      Map.of(
          DayOfWeek.MONDAY, "월",
          DayOfWeek.TUESDAY, "화",
          DayOfWeek.WEDNESDAY, "수",
          DayOfWeek.THURSDAY, "목",
          DayOfWeek.FRIDAY, "금",
          DayOfWeek.SATURDAY, "토",
          DayOfWeek.SUNDAY, "일");

  private final StoreRepository storeRepository;
  private final StoreBusinessHourRepository storeBusinessHourRepository;
  private final ReviewQueryService reviewQueryService;
  private final AddressService addressService;
  private final FavoriteRepository favoriteRepository;

  /**
   * 매장 상세 조회 (소비자).
   *
   * @param storeId 매장 ID
   * @param customerId 소비자 ID (거리·isFavorite 계산에 사용)
   * @throws BusinessException STORE_NOT_FOUND — 매장 없음/삭제
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없음
   */
  public ConsumerStoreDetailResponse getDetail(Long storeId, Long customerId) {
    // 1. 매장 조회 (소프트 삭제 제외)
    Store store =
        storeRepository
            .findByIdAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

    // 2. 기본 주소지 → 거리 계산 origin
    Point defaultLocation = addressService.requireDefaultLocation(customerId);
    double originLat = GeometryUtil.latitude(defaultLocation);
    double originLng = GeometryUtil.longitude(defaultLocation);

    // 3. 평점 / 리뷰수
    RatingStats ratingStats = reviewQueryService.getStoreRating(storeId);

    // 4. 거리 (m → km)
    Double distanceMeters = storeRepository.findDistanceMeters(storeId, originLat, originLng);
    double distanceKm = distanceMeters != null ? distanceMeters / 1000.0 : 0.0;

    // 5. isFavorite
    boolean isFavorite =
        !favoriteRepository
            .findStoreIdsByCustomerIdAndStoreIdIn(customerId, List.of(storeId))
            .isEmpty();

    // 6. 영업시간 (월~일 7개)
    Map<DayOfWeek, StoreBusinessHour> hourMap =
        storeBusinessHourRepository.findByStoreId(storeId).stream()
            .collect(Collectors.toMap(StoreBusinessHour::getDayOfWeek, Function.identity()));

    List<OperatingHourResponse> operatingHours = buildOperatingHours(hourMap);

    // 7. 오늘 영업 종료 시각
    DayOfWeek today = LocalDate.now().getDayOfWeek();
    String closingTime = null;
    if (hourMap.containsKey(today)) {
      closingTime = hourMap.get(today).getCloseTime().toString(); // HH:mm
    }

    // 8. lat/lng
    double lat = GeometryUtil.latitude(store.getLocation());
    double lng = GeometryUtil.longitude(store.getLocation());

    // 9. 주소 조합 (roadAddress + detailAddress)
    String address = buildAddress(store);

    return new ConsumerStoreDetailResponse(
        store.getId(),
        store.getName(),
        store.getImageUrl(),
        store.getOperationStatus(),
        closingTime,
        ratingStats.average(),
        ratingStats.count(),
        distanceKm,
        isFavorite,
        address,
        store.getPhone(),
        store.getBusinessNumber(),
        operatingHours,
        lat,
        lng);
  }

  // ── private helpers ──────────────────────────────────────────────────────────────────────────

  private List<OperatingHourResponse> buildOperatingHours(Map<DayOfWeek, StoreBusinessHour> map) {
    List<OperatingHourResponse> result = new ArrayList<>();
    for (DayOfWeek day : DAY_ORDER) {
      String label = DAY_LABEL.get(day);
      StoreBusinessHour hour = map.get(day);
      if (hour == null) {
        result.add(new OperatingHourResponse(label, null, null, true));
      } else {
        result.add(
            new OperatingHourResponse(
                label, hour.getOpenTime().toString(), hour.getCloseTime().toString(), false));
      }
    }
    return result;
  }

  private String buildAddress(Store store) {
    String detail = store.getDetailAddress();
    if (detail != null && !detail.isBlank()) {
      return store.getRoadAddress() + " " + detail;
    }
    return store.getRoadAddress();
  }
}
