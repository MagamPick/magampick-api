package com.magampick.store.service;

import com.magampick.address.domain.Address;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.repository.AddressRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.dto.StorePreviewInfo;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 상품 상세 화면 공통 매장 미리보기 조립. deal(ClearanceItemDetailQueryService) /
 * menu(ProductDetailQueryService) 양쪽에서 재사용.
 *
 * <p>거리 origin = 소비자 기본 주소지 (없으면 DEFAULT_ADDRESS_REQUIRED 400).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorePreviewHelper {

  private final StoreRepository storeRepository;
  private final StoreBusinessHourRepository storeBusinessHourRepository;
  private final AddressRepository addressRepository;

  /**
   * 매장 미리보기 공통 데이터 조립.
   *
   * @param storeId 매장 ID
   * @param customerId 소비자 ID
   * @return StorePreviewInfo (distanceKm, closingTime)
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없음
   */
  public StorePreviewInfo buildStorePreview(Long storeId, Long customerId) {
    // 1. 기본 주소지 → origin
    Address defaultAddress =
        addressRepository
            .findByCustomerIdAndIsDefaultTrue(customerId)
            .orElseThrow(() -> new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    double originLat = GeometryUtil.latitude(defaultAddress.getLocation());
    double originLng = GeometryUtil.longitude(defaultAddress.getLocation());

    // 2. 거리 (m → km)
    Double distanceMeters = storeRepository.findDistanceMeters(storeId, originLat, originLng);
    double distanceKm = distanceMeters != null ? distanceMeters / 1000.0 : 0.0;

    // 3. 오늘 영업 종료 시각 (오늘 휴무이면 null)
    DayOfWeek today = LocalDate.now().getDayOfWeek();
    Optional<StoreBusinessHour> hourToday =
        storeBusinessHourRepository.findByStoreIdAndDayOfWeek(storeId, today);
    String closingTime = hourToday.map(h -> h.getCloseTime().toString()).orElse(null);

    return new StorePreviewInfo(distanceKm, closingTime);
  }
}
