package com.magampick.store.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.storage.StorageService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.OperationStatus;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreBusinessHour;
import com.magampick.store.dto.BusinessHourPayload;
import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.dto.OperationStatusResponse;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.dto.StoreUpdateRequest;
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
   * 사업자 검증 — 등록 폼의 [조회하기] 버튼 대응. {@code nts.verification-mode} 에 따라 상태조회(status) 또는 진위확인(validate)을
   * 수행하며, 매장은 생성하지 않는다. 본 등록 호출({@link #registerStore})에서도 동일하게 다시 검증하므로 idempotent.
   */
  public void verifyBusiness(BusinessVerificationRequest request) {
    String businessNumber = normalizeBusinessNumber(request.businessNumber());
    businessVerificationService.verify(
        businessNumber, request.representativeName(), request.openDate());
  }

  /**
   * 매장 등록 (경로 B — 로그인 사장의 독립 등록). 사업자 검증 + 자체 DB 지오코딩 + 선택 이미지 업로드를 통과하면 자동 승인으로 즉시 생성된다.
   * operation_status 는 {@link OperationStatus#CLOSED_TODAY} 로 초기화 — 사장이 영업시간 입력 후 [영업 시작]으로 운영 개시.
   *
   * <p>외부 호출(검증·지오코딩·업로드)은 트랜잭션 시작 전에 처리하고 결과만 단일 INSERT(save)의 트랜잭션으로 반영한다 — 느린 외부 호출 동안 DB 트랜잭션을
   * 잡지 않기 위함. 가입 통합(경로 A) 의 다중 엔티티 롤백은 별도 작업.
   */
  public StoreRegisterResponse registerStore(
      Long sellerId, StoreCreateRequest request, MultipartFile image) {
    PreparedStoreRegistration prepared = prepareStoreRegistration(request, image);

    try {
      Seller seller =
          sellerRepository
              .findById(sellerId)
              .filter(s -> !s.isDeleted())
              .orElseThrow(() -> new BusinessException(SellerErrorCode.SELLER_NOT_FOUND));
      Store store = createStore(seller, prepared);

      log.info("매장 등록됨. storeId={}, sellerId={}", store.getId(), sellerId);
      return new StoreRegisterResponse(store.getId(), store.getOperationStatus());
    } catch (RuntimeException e) {
      deletePreparedImageBestEffort(prepared);
      throw e;
    }
  }

  public PreparedStoreRegistration prepareStoreRegistration(
      StoreCreateRequest request, MultipartFile image) {
    String businessNumber = normalizeBusinessNumber(request.businessNumber());
    validateOptionalImage(image);

    businessVerificationService.verify(
        businessNumber, request.representativeName(), request.openDate());
    Point location =
        geocodingService.geocode(
            new GeocodeQuery(request.sigunguCode(), request.roadnameCode(), request.roadAddress()));
    String imageUrl = uploadOptionalStoreImage(image);
    return new PreparedStoreRegistration(businessNumber, request, location, imageUrl);
  }

  public Store createStore(Seller seller, PreparedStoreRegistration prepared) {
    StoreCreateRequest request = prepared.request();
    return storeRepository.save(
        Store.builder()
            .seller(seller)
            .businessNumber(prepared.businessNumber())
            .name(request.name())
            .roadAddress(request.roadAddress())
            .jibunAddress(request.jibunAddress())
            .detailAddress(request.detailAddress())
            .zonecode(request.zonecode())
            .location(prepared.location())
            .phone(request.phone())
            .description(request.description())
            .imageUrl(prepared.imageUrl())
            .operationStatus(OperationStatus.CLOSED_TODAY)
            .build());
  }

  public void deletePreparedImageBestEffort(PreparedStoreRegistration prepared) {
    if (prepared == null) {
      return;
    }
    deleteImageBestEffort(prepared.imageUrl());
  }

  @Transactional(readOnly = true)
  public List<StoreResponse> getMyStores(Long sellerId) {
    return storeRepository.findBySellerIdOrderByCreatedAtAsc(sellerId).stream()
        .map(storeMapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public StoreDetailResponse getMyStore(Long sellerId, Long storeId) {
    Store store =
        storeRepository
            .findByIdAndSellerId(storeId, sellerId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
    return storeMapper.toDetailResponse(store);
  }

  /**
   * 매장 정보 수정 (부분 수정 — null = 변경 X). 변경된 주소는 자체 DB 지오코딩 재호출, 변경된 사진은 OCI 재업로드 + 기존 사진 best effort
   * 삭제. 사업자번호·영업상태·영업시간은 비범위 (요청에서 무시). 외부 호출(지오코딩·업로드)은 트랜잭션 시작 전, 결과만 단일 UPDATE(save)에 반영 — PR-A
   * 등록과 동일 패턴. 기존 사진 삭제는 성공 후 best effort (실패해도 흐름 정상 진행).
   */
  public StoreDetailResponse updateStore(
      Long sellerId, Long storeId, StoreUpdateRequest request, MultipartFile image) {
    Store store = findOwnedStore(sellerId, storeId);

    boolean addressChanged =
        request.roadAddress() != null && !request.roadAddress().equals(store.getRoadAddress());
    boolean photoChanged = hasImage(image);
    if (photoChanged) {
      validateImage(image);
    }

    Point newLocation =
        addressChanged
            ? geocodingService.geocode(
                new GeocodeQuery(
                    request.sigunguCode(), request.roadnameCode(), request.roadAddress()))
            : null;
    String newImageUrl = photoChanged ? uploadStoreImage(image) : null;
    String oldImageUrl = photoChanged ? store.getImageUrl() : null;

    applyUpdate(store, request, addressChanged, newLocation, photoChanged, newImageUrl);
    Store updated = storeRepository.save(store);
    log.info("매장 정보 수정. storeId={}, sellerId={}", store.getId(), sellerId);

    if (photoChanged) {
      deleteImageBestEffort(oldImageUrl);
    }
    return storeMapper.toDetailResponse(updated);
  }

  private void applyUpdate(
      Store store,
      StoreUpdateRequest request,
      boolean addressChanged,
      Point newLocation,
      boolean photoChanged,
      String newImageUrl) {
    if (request.name() != null) {
      store.changeName(request.name());
    }
    if (request.phone() != null) {
      store.changePhone(request.phone());
    }
    if (request.description() != null) {
      store.changeDescription(request.description());
    }
    if (request.detailAddress() != null) {
      store.changeDetailAddress(request.detailAddress());
    }
    if (addressChanged) {
      String jibun =
          request.jibunAddress() != null ? request.jibunAddress() : store.getJibunAddress();
      String zone = request.zonecode() != null ? request.zonecode() : store.getZonecode();
      store.changeAddress(request.roadAddress(), jibun, zone, newLocation);
    }
    if (photoChanged) {
      store.changeImageUrl(newImageUrl);
    }
  }

  private void deleteImageBestEffort(String url) {
    if (url == null) {
      return;
    }
    try {
      storageService.delete(url);
    } catch (RuntimeException e) {
      log.warn("기존 매장 사진 삭제 실패 (무시). url={}", url, e);
    }
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

  /** 본인 매장의 영업 요일별 영업시간 (영업 요일만, 휴무 요일은 row 없음). */
  @Transactional(readOnly = true)
  public List<BusinessHourPayload> getBusinessHours(Long sellerId, Long storeId) {
    Store store = findOwnedStore(sellerId, storeId);
    return storeBusinessHourRepository.findByStoreId(store.getId()).stream()
        .map(this::toPayload)
        .toList();
  }

  /**
   * 매장의 요일별 영업시간을 전체 교체로 저장한다 (delete-all + save-all). 휴무 요일은 입력 list 에서 제외 — 빈 list 도 허용 (모든 요일
   * 휴무). 매장 영업 상태가 {@link OperationStatus#OPEN} 이면 오늘 요일 row 의 시간 변경·삭제는 거부 ({@code
   * TODAY_BUSINESS_HOURS_LOCKED}) — 다른 요일 변경 / 오늘 신규 추가는 허용 (노션 "영업시간 설정").
   */
  @Transactional
  public List<BusinessHourPayload> saveBusinessHours(
      Long sellerId, Long storeId, List<BusinessHourPayload> hours) {
    validateRanges(hours);
    validateNoDuplicates(hours);

    Store store = findOwnedStore(sellerId, storeId);

    List<StoreBusinessHour> prev = storeBusinessHourRepository.findByStoreId(store.getId());
    validateTodayLockIfOpen(store, prev, hours);

    storeBusinessHourRepository.deleteByStoreId(store.getId());
    List<StoreBusinessHour> next =
        hours.stream()
            .map(
                p ->
                    StoreBusinessHour.builder()
                        .store(store)
                        .dayOfWeek(p.day())
                        .openTime(p.openTime())
                        .closeTime(p.closeTime())
                        .build())
            .toList();
    storeBusinessHourRepository.saveAll(next);
    log.info(
        "매장 영업시간 저장. storeId={}, sellerId={}, rowCount={}", store.getId(), sellerId, hours.size());
    return hours;
  }

  private Store findOwnedStore(Long sellerId, Long storeId) {
    return storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
  }

  private BusinessHourPayload toPayload(StoreBusinessHour h) {
    return new BusinessHourPayload(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime());
  }

  /** 시작 시각 &lt; 종료 시각 (같은 날 내). 노션 "영업시간 입력 형식". */
  private void validateRanges(List<BusinessHourPayload> hours) {
    for (BusinessHourPayload h : hours) {
      if (!h.openTime().isBefore(h.closeTime())) {
        throw new BusinessException(StoreErrorCode.BUSINESS_HOURS_INVALID_RANGE);
      }
    }
  }

  /** 같은 요일 중복 row 차단 (DB UNIQUE 사전 검증 — `BUSINESS_HOURS_INVALID_RANGE` 재사용). */
  private void validateNoDuplicates(List<BusinessHourPayload> hours) {
    long distinct = hours.stream().map(BusinessHourPayload::day).distinct().count();
    if (distinct != hours.size()) {
      throw new BusinessException(StoreErrorCode.BUSINESS_HOURS_INVALID_RANGE);
    }
  }

  /**
   * OPEN 중 오늘 요일 변경 잠금 — 오늘 요일이 prev 에 있고 (next 에서 빠지거나 시간이 다르면) 거부. prev 에 없으면 신규 추가는 허용. (노션 "영업
   * 중 영업시간 변경 제한" + FE {@code hasTodayHoursChanged} 정합)
   */
  private void validateTodayLockIfOpen(
      Store store, List<StoreBusinessHour> prev, List<BusinessHourPayload> next) {
    if (store.getOperationStatus() != OperationStatus.OPEN) {
      return;
    }
    DayOfWeek today = todayDayOfWeek();
    Optional<StoreBusinessHour> prevToday =
        prev.stream().filter(h -> h.getDayOfWeek() == today).findFirst();
    if (prevToday.isEmpty()) {
      return; // 이전 휴무 → 추가 허용
    }
    Optional<BusinessHourPayload> nextToday =
        next.stream().filter(p -> p.day() == today).findFirst();
    if (nextToday.isEmpty()) {
      throw new BusinessException(StoreErrorCode.TODAY_BUSINESS_HOURS_LOCKED); // 삭제(휴무 전환)
    }
    StoreBusinessHour p = prevToday.get();
    BusinessHourPayload n = nextToday.get();
    if (!p.getOpenTime().equals(n.openTime()) || !p.getCloseTime().equals(n.closeTime())) {
      throw new BusinessException(StoreErrorCode.TODAY_BUSINESS_HOURS_LOCKED); // 시간 수정
    }
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

  private void validateOptionalImage(MultipartFile image) {
    if (hasImage(image)) {
      validateImage(image);
    }
  }

  private String uploadOptionalStoreImage(MultipartFile image) {
    if (!hasImage(image)) {
      return null;
    }
    return uploadStoreImage(image);
  }

  private void validateImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
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

  private boolean hasImage(MultipartFile image) {
    return image != null && !image.isEmpty();
  }
}
