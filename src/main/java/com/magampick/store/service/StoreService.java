package com.magampick.store.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.storage.StorageService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.dto.OperationStatusResponse;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.mapper.StoreMapper;
import com.magampick.store.repository.StoreBusinessHourRepository;
import com.magampick.store.repository.StoreRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

  private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final StoreRepository storeRepository;
  private final StoreBusinessHourRepository storeBusinessHourRepository;
  private final SellerRepository sellerRepository;
  private final StorageService storageService;
  private final BusinessVerificationService businessVerificationService;
  private final GeocodingService geocodingService;
  private final StoreMapper storeMapper;

  /**
   * 사업자 진위확인 — 등록 폼의 [조회하기] 버튼 대응. 사업자번호·대표자명·개업일자 세 값 일치 여부만 검증, 매장은 생성하지 않는다. 본 등록 호출({@link
   * #registerStore})에서도 동일하게 다시 검증하므로 idempotent.
   */
  public void verifyBusiness(BusinessVerificationRequest request) {
    String businessNumber = normalizeBusinessNumber(request.businessNumber());
    businessVerificationService.verify(
        businessNumber, request.representativeName(), request.openDate());
  }

  /**
   * 매장 등록 (경로 B — 로그인 사장의 독립 등록). 사업자 진위확인(번호·대표자명·개업일자) + 지오코딩 + 이미지 업로드를 통과하면 자동 승인으로 즉시 생성된다.
   * operation_status 는 {@link OperationStatus#CLOSED_TODAY} 로 초기화 — 사장이 영업시간 입력 후 [영업 시작]으로 운영 개시.
   *
   * <p>외부 호출(검증·지오코딩·업로드)은 트랜잭션 시작 전에 처리하고 결과만 단일 INSERT(save)의 트랜잭션으로 반영한다 — 느린 외부 호출 동안 DB 트랜잭션을
   * 잡지 않기 위함. 가입 통합(경로 A) 의 다중 엔티티 롤백은 별도 작업.
   */
  public StoreRegisterResponse registerStore(
      Long sellerId, StoreCreateRequest request, MultipartFile image) {
    String businessNumber = normalizeBusinessNumber(request.businessNumber());
    validateImage(image);

    businessVerificationService.verify(
        businessNumber, request.representativeName(), request.openDate());
    Point location = geocodingService.geocode(request.roadAddress());
    String imageUrl = uploadStoreImage(image);

    Seller seller =
        sellerRepository
            .findById(sellerId)
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new BusinessException(SellerErrorCode.SELLER_NOT_FOUND));

    Store store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber(businessNumber)
                .name(request.name())
                .roadAddress(request.roadAddress())
                .jibunAddress(request.jibunAddress())
                .detailAddress(request.detailAddress())
                .zonecode(request.zonecode())
                .location(location)
                .phone(request.phone())
                .description(request.description())
                .imageUrl(imageUrl)
                .operationStatus(OperationStatus.CLOSED_TODAY)
                .build());

    log.info("매장 등록됨. storeId={}, sellerId={}", store.getId(), sellerId);
    return new StoreRegisterResponse(store.getId(), store.getOperationStatus());
  }

  @Transactional(readOnly = true)
  public List<StoreResponse> getMyStores(Long sellerId) {
    return storeRepository.findBySellerId(sellerId).stream().map(storeMapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public StoreDetailResponse getMyStore(Long sellerId, Long storeId) {
    Store store =
        storeRepository
            .findByIdAndSellerId(storeId, sellerId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
    return storeMapper.toDetailResponse(store);
  }

  /** 본인 매장 영업 상태 + 오늘 영업 요일 여부 + 오늘 마감 시각 조회. */
  @Transactional(readOnly = true)
  public OperationStatusResponse getOperationStatus(Long sellerId, Long storeId) {
    Store store = findOwnedStore(sellerId, storeId);
    return toOperationStatusResponse(store);
  }

  /**
   * 본인 매장 영업 상태 전이. 노션 "매장 영업 상태 관리" 의 5개 허용 전이만 통과하며, {@link OperationStatus#OPEN} 진입은 오늘 요일이 영업
   * 요일일 때만 허용된다 (오늘 요일의 영업시간 row 존재 여부로 판정). 자동 전환은 없다 — 사장 명시 호출.
   */
  @Transactional
  public OperationStatusResponse transitionOperationStatus(
      Long sellerId, Long storeId, OperationStatus to) {
    Store store = findOwnedStore(sellerId, storeId);
    Optional<StoreBusinessHour> todayHour = findTodayBusinessHour(store.getId());
    validateTransition(store.getOperationStatus(), to, todayHour.isPresent());
    store.changeOperationStatus(to);
    log.info("매장 영업 상태 전이. storeId={}, sellerId={}, to={}", store.getId(), sellerId, to);
    return toOperationStatusResponse(store, todayHour);
  }

  private Store findOwnedStore(Long sellerId, Long storeId) {
    return storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
  }

  /** 노션 전이 그래프. 자기 전이와 {@code CLOSED_TODAY → BREAK} 는 항상 거부, OPEN 진입은 영업 요일 검사. */
  private void validateTransition(OperationStatus from, OperationStatus to, boolean canOpenToday) {
    if (from == to) {
      throw new BusinessException(StoreErrorCode.INVALID_STATE_TRANSITION);
    }
    switch (to) {
      case OPEN -> {
        // OPEN 진입은 CLOSED_TODAY 또는 BREAK 에서만
        if (from != OperationStatus.CLOSED_TODAY && from != OperationStatus.BREAK) {
          throw new BusinessException(StoreErrorCode.INVALID_STATE_TRANSITION);
        }
        if (!canOpenToday) {
          throw new BusinessException(StoreErrorCode.STORE_CLOSED_TODAY);
        }
      }
      case BREAK -> {
        // BREAK 는 OPEN 에서만 (CLOSED_TODAY → BREAK 금지)
        if (from != OperationStatus.OPEN) {
          throw new BusinessException(StoreErrorCode.INVALID_STATE_TRANSITION);
        }
      }
      case CLOSED_TODAY -> {
        // CLOSED_TODAY 는 OPEN/BREAK 에서만
        if (from != OperationStatus.OPEN && from != OperationStatus.BREAK) {
          throw new BusinessException(StoreErrorCode.INVALID_STATE_TRANSITION);
        }
      }
    }
  }

  private Optional<StoreBusinessHour> findTodayBusinessHour(Long storeId) {
    return storeBusinessHourRepository.findByStoreIdAndDayOfWeek(storeId, todayDayOfWeek());
  }

  private OperationStatusResponse toOperationStatusResponse(Store store) {
    return toOperationStatusResponse(store, findTodayBusinessHour(store.getId()));
  }

  private OperationStatusResponse toOperationStatusResponse(
      Store store, Optional<StoreBusinessHour> todayHour) {
    LocalTime closeTime = todayHour.map(StoreBusinessHour::getCloseTime).orElse(null);
    return new OperationStatusResponse(
        store.getId(), store.getOperationStatus(), todayHour.isPresent(), closeTime);
  }

  private DayOfWeek todayDayOfWeek() {
    return LocalDate.now(KST).getDayOfWeek();
  }

  private String normalizeBusinessNumber(String raw) {
    String digits = raw == null ? "" : raw.replace("-", "");
    if (!digits.matches("\\d{10}")) {
      throw new BusinessException(StoreErrorCode.BUSINESS_NUMBER_FORMAT_INVALID);
    }
    return digits;
  }

  private String uploadStoreImage(MultipartFile image) {
    try {
      return storageService.upload(image);
    } catch (BusinessException e) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_UPLOAD_FAILED, e);
    }
  }

  private void validateImage(MultipartFile image) {
    if (image.isEmpty()) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_INVALID_TYPE);
    }
    if (image.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_TOO_LARGE);
    }
    String contentType = image.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_INVALID_TYPE);
    }
  }
}
