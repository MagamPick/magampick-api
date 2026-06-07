package com.magampick.clearance.service;

import com.magampick.address.domain.Address;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.dto.ClosingDealResponse;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.clearance.repository.ClosingDealCandidate;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 홈 피드 마감 임박 특가 조회 서비스. 기본 주소지 5km · OPEN · 오늘영업 매장의 60분 이내 활성 떨이를 마감 가까운 순 top5 반환. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClosingDealQueryService {

  private final AddressRepository addressRepository;
  private final ClearanceItemRepository clearanceItemRepository;

  /**
   * 마감 임박 특가 목록 조회.
   *
   * @param customerId 소비자 ID
   * @return 60분 이내 마감 활성 떨이 목록 (최대 5개, 마감 빠른 순). 빈 결과이면 빈 리스트.
   * @throws BusinessException DEFAULT_ADDRESS_REQUIRED — 기본 주소지 없을 때
   */
  public List<ClosingDealResponse> getClosingSoonDeals(Long customerId) {
    // 1. origin — 기본 주소지
    Address defaultAddress =
        addressRepository
            .findByCustomerIdAndIsDefaultTrue(customerId)
            .orElseThrow(() -> new BusinessException(AddressErrorCode.DEFAULT_ADDRESS_REQUIRED));

    double lat = GeometryUtil.latitude(defaultAddress.getLocation());
    double lng = GeometryUtil.longitude(defaultAddress.getLocation());
    String today = LocalDate.now().getDayOfWeek().name();
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime until = now.plusMinutes(60);

    // 2. 60분 윈도우 후보 쿼리 (LIMIT 5, ORDER BY pickup_end_at ASC)
    List<ClosingDealCandidate> candidates =
        clearanceItemRepository.findClosingSoonDeals(lat, lng, today, now, until);

    // 3. discountRate 계산 + 매핑
    return candidates.stream().map(this::toResponse).toList();
  }

  private ClosingDealResponse toResponse(ClosingDealCandidate c) {
    // discountRate(%) = (regular - sale) / regular * 100, HALF_UP 반올림
    BigDecimal regular = c.getRegularPrice();
    BigDecimal sale = c.getSalePrice();
    int discountRate =
        regular
            .subtract(sale)
            .divide(regular, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .intValue();

    return new ClosingDealResponse(
        c.getId(),
        c.getStoreName(),
        c.getProductName(),
        c.getImageUrl(),
        discountRate,
        regular,
        sale,
        c.getPickupDeadline());
  }
}
