package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

  @Mock StoreRepository storeRepository;
  @Mock StoreBusinessHourRepository storeBusinessHourRepository;
  @Mock SellerRepository sellerRepository;
  @Mock StorageService storageService;
  @Mock BusinessVerificationService businessVerificationService;
  @Mock GeocodingService geocodingService;
  @Mock StoreMapper storeMapper;
  @InjectMocks StoreService storeService;

  private static final Long SELLER_ID = 1L;
  private static final Long STORE_ID = 10L;
  private static final String OWNER_NAME = "홍길동";
  private static final LocalDate OPEN_DATE = LocalDate.of(2024, 3, 15);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private Seller seller() {
    Seller s =
        Seller.builder()
            .email("seller@test.com")
            .passwordHash("hash")
            .ownerName(OWNER_NAME)
            .businessNumber("1234567890")
            .build();
    ReflectionTestUtils.setField(s, "id", SELLER_ID);
    return s;
  }

  private Store store(Long id, Seller seller) {
    Store s =
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .imageUrl("/uploads/2026/5/uuid.jpg")
            .operationStatus(OperationStatus.CLOSED_TODAY)
            .build();
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }

  private StoreCreateRequest createRequest() {
    return createRequest("123-45-67890");
  }

  private StoreCreateRequest createRequest(String businessNumber) {
    return new StoreCreateRequest(
        businessNumber,
        OWNER_NAME,
        OPEN_DATE,
        "동네빵집",
        "서울 강남구 테헤란로 427",
        null,
        null,
        "06158",
        "0212345678",
        "신선한 빵");
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[1024]);
  }

  private void stubExternalSuccess() {
    given(geocodingService.geocode(any())).willReturn(GeometryUtil.toPoint(37.5, 127.0));
    given(storageService.upload(any())).willReturn("/uploads/2026/5/uuid.jpg");
    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller()));
    given(storeRepository.save(any(Store.class)))
        .willAnswer(
            inv -> {
              Store s = inv.getArgument(0);
              ReflectionTestUtils.setField(s, "id", STORE_ID);
              return s;
            });
  }

  // ── 사업자 진위확인 (별도 endpoint) ──────────────────────────────────────────

  @Test
  void 사업자_진위확인_성공_하이픈_제거_정규화() {
    // given
    BusinessVerificationRequest request =
        new BusinessVerificationRequest("123-45-67890", OWNER_NAME, OPEN_DATE);

    // when
    storeService.verifyBusiness(request);

    // then — 정규화된 10자리 + 대표자명·개업일자가 그대로 외부 호출에 전달
    then(businessVerificationService).should().verify("1234567890", OWNER_NAME, OPEN_DATE);
  }

  @Test
  void 사업자_진위확인_형식_오류_예외() {
    // given
    BusinessVerificationRequest request =
        new BusinessVerificationRequest("12345", OWNER_NAME, OPEN_DATE);

    // when / then
    assertThatThrownBy(() -> storeService.verifyBusiness(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_NUMBER_FORMAT_INVALID);
    then(businessVerificationService).should(never()).verify(any(), any(), any());
  }

  @Test
  void 사업자_진위확인_불일치_예외() {
    // given
    BusinessVerificationRequest request =
        new BusinessVerificationRequest("000-00-00000", OWNER_NAME, OPEN_DATE);
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH))
        .given(businessVerificationService)
        .verify(any(), any(), any());

    // when / then
    assertThatThrownBy(() -> storeService.verifyBusiness(request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_INFO_MISMATCH);
  }

  // ── 매장 등록 ──────────────────────────────────────────────────────────────

  @Test
  void 매장_등록_성공_자동생성_및_사업자번호_정규화() {
    // given
    stubExternalSuccess();

    // when
    StoreRegisterResponse response =
        storeService.registerStore(SELLER_ID, createRequest(), validImage());

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
    then(businessVerificationService).should().verify("1234567890", OWNER_NAME, OPEN_DATE);
    then(geocodingService).should().geocode("서울 강남구 테헤란로 427");
    then(storageService).should().upload(any());

    ArgumentCaptor<Store> captor = ArgumentCaptor.forClass(Store.class);
    then(storeRepository).should().save(captor.capture());
    assertThat(captor.getValue().getOperationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
    assertThat(captor.getValue().getBusinessNumber()).isEqualTo("1234567890");
  }

  @Test
  void 매장_등록_사업자번호_형식_오류_예외() {
    // given - 하이픈 제거 후 10자리 숫자 아님
    StoreCreateRequest request = createRequest("12345");

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, request, validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_NUMBER_FORMAT_INVALID);
    then(businessVerificationService).should(never()).verify(any(), any(), any());
    then(storageService).should(never()).upload(any());
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_국세청_정상영업_아님_예외() {
    // given
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE))
        .given(businessVerificationService)
        .verify(any(), any(), any());

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_NUMBER_NOT_ACTIVE);
    then(geocodingService).should(never()).geocode(any());
    then(storageService).should(never()).upload(any());
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_진위확인_불일치_예외() {
    // given
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_INFO_MISMATCH))
        .given(businessVerificationService)
        .verify(any(), any(), any());

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_INFO_MISMATCH);
    then(geocodingService).should(never()).geocode(any());
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_국세청_API_장애_예외() {
    // given
    willThrow(new BusinessException(StoreErrorCode.BUSINESS_NUMBER_VERIFICATION_FAILED))
        .given(businessVerificationService)
        .verify(any(), any(), any());

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", StoreErrorCode.BUSINESS_NUMBER_VERIFICATION_FAILED);
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_지오코딩_실패_예외() {
    // given
    given(geocodingService.geocode(any()))
        .willThrow(new BusinessException(StoreErrorCode.ADDRESS_GEOCODING_FAILED));

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.ADDRESS_GEOCODING_FAILED);
    then(storageService).should(never()).upload(any());
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_이미지_업로드_실패_예외() {
    // given
    given(geocodingService.geocode(any())).willReturn(GeometryUtil.toPoint(37.5, 127.0));
    given(storageService.upload(any()))
        .willThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR));

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_IMAGE_UPLOAD_FAILED);
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_파일_크기_초과_예외() {
    // given
    MockMultipartFile bigFile =
        new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), bigFile))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_IMAGE_TOO_LARGE);
  }

  @Test
  void 매장_등록_파일_형식_불가_예외() {
    // given
    MockMultipartFile gifFile =
        new MockMultipartFile("image", "anim.gif", "image/gif", new byte[1024]);

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), gifFile))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_IMAGE_INVALID_TYPE);
  }

  @Test
  void 매장_등록_seller_없음_예외() {
    // given
    given(geocodingService.geocode(any())).willReturn(GeometryUtil.toPoint(37.5, 127.0));
    given(storageService.upload(any())).willReturn("/uploads/uuid.jpg");
    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.SELLER_NOT_FOUND);
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_등록_동일_사업자번호_중복_허용() {
    // given - 동일 사업자번호로 두 번 등록해도 거부하지 않는다 (UNIQUE 없음)
    stubExternalSuccess();

    // when
    StoreRegisterResponse first =
        storeService.registerStore(SELLER_ID, createRequest("1234567890"), validImage());
    StoreRegisterResponse second =
        storeService.registerStore(SELLER_ID, createRequest("1234567890"), validImage());

    // then
    assertThat(first.storeId()).isNotNull();
    assertThat(second.storeId()).isNotNull();
    then(storeRepository).should(times(2)).save(any(Store.class));
    then(businessVerificationService)
        .should(times(2))
        .verify(eq("1234567890"), eq(OWNER_NAME), eq(OPEN_DATE));
  }

  // ── 사장 조회 ──────────────────────────────────────────────────────────────

  @Test
  void 본인_매장_목록_조회_성공() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findBySellerId(SELLER_ID)).willReturn(List.of(s));
    given(storeMapper.toResponse(s))
        .willReturn(
            new StoreResponse(
                STORE_ID,
                "동네빵집",
                "서울 강남구",
                null,
                "0212345678",
                "/uploads/uuid.jpg",
                OperationStatus.CLOSED_TODAY,
                OffsetDateTime.now()));

    // when
    List<StoreResponse> result = storeService.getMyStores(SELLER_ID);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(STORE_ID);
    assertThat(result.get(0).operationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
  }

  @Test
  void 본인_매장_상세_조회_성공() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeMapper.toDetailResponse(s))
        .willReturn(
            new StoreDetailResponse(
                STORE_ID,
                "1234567890",
                "동네빵집",
                "서울 강남구",
                null,
                null,
                "06158",
                37.5,
                127.0,
                "0212345678",
                "소개",
                "/uploads/uuid.jpg",
                OffsetDateTime.now()));

    // when
    StoreDetailResponse result = storeService.getMyStore(SELLER_ID, STORE_ID);

    // then
    assertThat(result.id()).isEqualTo(STORE_ID);
    assertThat(result.businessNumber()).isEqualTo("1234567890");
  }

  @Test
  void 본인_매장_상세_조회_소유권_없음_예외() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> storeService.getMyStore(SELLER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── 영업 상태 조회 ──────────────────────────────────────────────────────────

  private StoreBusinessHour businessHour(Store s, DayOfWeek day, LocalTime open, LocalTime close) {
    return StoreBusinessHour.builder()
        .store(s)
        .dayOfWeek(day)
        .openTime(open)
        .closeTime(close)
        .build();
  }

  @Test
  void 영업_상태_조회_성공_오늘_영업_요일() {
    // given
    Store s = store(STORE_ID, seller()); // 초기 CLOSED_TODAY
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(STORE_ID, today))
        .willReturn(Optional.of(businessHour(s, today, LocalTime.of(9, 0), LocalTime.of(21, 0))));

    // when
    OperationStatusResponse response = storeService.getOperationStatus(SELLER_ID, STORE_ID);

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
    assertThat(response.canOpenToday()).isTrue();
    assertThat(response.todayCloseTime()).isEqualTo(LocalTime.of(21, 0));
  }

  @Test
  void 영업_상태_조회_성공_오늘_휴무() {
    // given - 오늘 요일 row 없음 = 오늘 휴무
    Store s = store(STORE_ID, seller());
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreIdAndDayOfWeek(STORE_ID, today))
        .willReturn(Optional.empty());

    // when
    OperationStatusResponse response = storeService.getOperationStatus(SELLER_ID, STORE_ID);

    // then
    assertThat(response.canOpenToday()).isFalse();
    assertThat(response.todayCloseTime()).isNull();
  }

  @Test
  void 영업_상태_조회_소유권_없음_예외() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> storeService.getOperationStatus(SELLER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── 영업 상태 전이 ──────────────────────────────────────────────────────────

  private void stubBusinessDay(boolean isBusinessDay) {
    if (isBusinessDay) {
      given(
              storeBusinessHourRepository.findByStoreIdAndDayOfWeek(
                  eq(STORE_ID), any(DayOfWeek.class)))
          .willReturn(
              Optional.of(
                  StoreBusinessHour.builder()
                      .store(store(STORE_ID, seller()))
                      .dayOfWeek(LocalDate.now(KST).getDayOfWeek())
                      .openTime(LocalTime.of(9, 0))
                      .closeTime(LocalTime.of(21, 0))
                      .build()));
    } else {
      given(
              storeBusinessHourRepository.findByStoreIdAndDayOfWeek(
                  eq(STORE_ID), any(DayOfWeek.class)))
          .willReturn(Optional.empty());
    }
  }

  @Test
  void 영업_상태_전이_CLOSED_TODAY_to_OPEN_성공_영업_요일() {
    // given - 초기 CLOSED_TODAY + 오늘 영업 요일
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    stubBusinessDay(true);

    // when
    OperationStatusResponse response =
        storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.OPEN);

    // then
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.OPEN);
    assertThat(s.getOperationStatus()).isEqualTo(OperationStatus.OPEN);
    assertThat(response.canOpenToday()).isTrue();
  }

  @Test
  void 영업_상태_전이_CLOSED_TODAY_to_OPEN_거부_휴무_요일() {
    // given - 초기 CLOSED_TODAY + 오늘 휴무
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    stubBusinessDay(false);

    // when / then
    assertThatThrownBy(
            () -> storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.OPEN))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_CLOSED_TODAY);
    assertThat(s.getOperationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY); // 변경 X
  }

  @Test
  void 영업_상태_전이_BREAK_to_OPEN_성공_영업_요일() {
    // given
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.BREAK);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    stubBusinessDay(true);

    // when
    OperationStatusResponse response =
        storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.OPEN);

    // then
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.OPEN);
  }

  @Test
  void 영업_상태_전이_OPEN_to_BREAK_성공() {
    // given
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));

    // when
    OperationStatusResponse response =
        storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.BREAK);

    // then
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.BREAK);
    assertThat(s.getOperationStatus()).isEqualTo(OperationStatus.BREAK);
  }

  @Test
  void 영업_상태_전이_OPEN_to_CLOSED_TODAY_성공() {
    // given
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));

    // when
    OperationStatusResponse response =
        storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.CLOSED_TODAY);

    // then
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
  }

  @Test
  void 영업_상태_전이_BREAK_to_CLOSED_TODAY_성공() {
    // given
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.BREAK);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));

    // when
    OperationStatusResponse response =
        storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.CLOSED_TODAY);

    // then
    assertThat(response.operationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
  }

  @Test
  void 영업_상태_전이_CLOSED_TODAY_to_BREAK_거부_금지_전이() {
    // given - CLOSED_TODAY 에서 BREAK 진입은 금지 (의미 모순)
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));

    // when / then
    assertThatThrownBy(
            () ->
                storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.BREAK))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.INVALID_STATE_TRANSITION);
    assertThat(s.getOperationStatus()).isEqualTo(OperationStatus.CLOSED_TODAY);
  }

  @Test
  void 영업_상태_전이_자기_전이_거부() {
    // given - OPEN → OPEN 자기 전이는 금지
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));

    // when / then
    assertThatThrownBy(
            () -> storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.OPEN))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.INVALID_STATE_TRANSITION);
  }

  @Test
  void 영업_상태_전이_소유권_없음_예외() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> storeService.transitionOperationStatus(SELLER_ID, STORE_ID, OperationStatus.OPEN))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── 영업시간 조회 ──────────────────────────────────────────────────────────

  private StoreBusinessHour hourOf(Store s, DayOfWeek day, LocalTime open, LocalTime close) {
    return StoreBusinessHour.builder()
        .store(s)
        .dayOfWeek(day)
        .openTime(open)
        .closeTime(close)
        .build();
  }

  private BusinessHourPayload payload(DayOfWeek day, LocalTime open, LocalTime close) {
    return new BusinessHourPayload(day, open, close);
  }

  @Test
  void 영업시간_조회_성공_영업_요일만() {
    // given - 월요일·화요일만 영업, 나머지 휴무
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID))
        .willReturn(
            List.of(
                hourOf(s, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                hourOf(s, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))));

    // when
    List<BusinessHourPayload> result = storeService.getBusinessHours(SELLER_ID, STORE_ID);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).day()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(result.get(1).day()).isEqualTo(DayOfWeek.TUESDAY);
  }

  @Test
  void 영업시간_조회_빈_리스트() {
    // given - 영업 요일 0개
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    // when
    List<BusinessHourPayload> result = storeService.getBusinessHours(SELLER_ID, STORE_ID);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void 영업시간_조회_소유권_없음_예외() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> storeService.getBusinessHours(SELLER_ID, STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── 영업시간 저장 ──────────────────────────────────────────────────────────

  @Test
  void 영업시간_저장_성공_전체_교체() {
    // given - prev 비어 있음, new 7개 다 영업
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    List<BusinessHourPayload> req =
        java.util.Arrays.stream(DayOfWeek.values())
            .map(d -> payload(d, LocalTime.of(9, 0), LocalTime.of(21, 0)))
            .toList();

    // when
    List<BusinessHourPayload> result = storeService.saveBusinessHours(SELLER_ID, STORE_ID, req);

    // then
    assertThat(result).hasSize(7);
    then(storeBusinessHourRepository).should().deleteByStoreId(STORE_ID);
    then(storeBusinessHourRepository).should().saveAll(any(Iterable.class));
  }

  @Test
  void 영업시간_저장_빈_리스트_모든_요일_휴무_허용() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    // when
    List<BusinessHourPayload> result =
        storeService.saveBusinessHours(SELLER_ID, STORE_ID, List.of());

    // then - 거부 없이 통과, prev 도 비어있어 삭제는 호출되지만 결과는 빈 리스트
    assertThat(result).isEmpty();
    then(storeBusinessHourRepository).should().deleteByStoreId(STORE_ID);
  }

  @Test
  void 영업시간_저장_시작_종료_역전_예외() {
    // given - 오픈 > 마감
    List<BusinessHourPayload> req =
        List.of(payload(DayOfWeek.MONDAY, LocalTime.of(21, 0), LocalTime.of(9, 0)));

    // when / then
    assertThatThrownBy(() -> storeService.saveBusinessHours(SELLER_ID, STORE_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_HOURS_INVALID_RANGE);
    then(storeBusinessHourRepository).should(never()).deleteByStoreId(any());
  }

  @Test
  void 영업시간_저장_시작_종료_동일_예외() {
    // given - 오픈 == 마감
    List<BusinessHourPayload> req =
        List.of(payload(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 0)));

    // when / then
    assertThatThrownBy(() -> storeService.saveBusinessHours(SELLER_ID, STORE_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_HOURS_INVALID_RANGE);
  }

  @Test
  void 영업시간_저장_같은_요일_중복_예외() {
    // given - 월요일 두 row
    List<BusinessHourPayload> req =
        List.of(
            payload(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            payload(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(21, 0)));

    // when / then
    assertThatThrownBy(() -> storeService.saveBusinessHours(SELLER_ID, STORE_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.BUSINESS_HOURS_INVALID_RANGE);
  }

  @Test
  void 영업시간_저장_OPEN_오늘_요일_시간_수정_거부() {
    // given - OPEN + 오늘 요일 시간 변경
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID))
        .willReturn(List.of(hourOf(s, today, LocalTime.of(9, 0), LocalTime.of(21, 0))));

    List<BusinessHourPayload> req =
        List.of(payload(today, LocalTime.of(10, 0), LocalTime.of(22, 0)));

    // when / then
    assertThatThrownBy(() -> storeService.saveBusinessHours(SELLER_ID, STORE_ID, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.TODAY_BUSINESS_HOURS_LOCKED);
    then(storeBusinessHourRepository).should(never()).deleteByStoreId(any());
  }

  @Test
  void 영업시간_저장_OPEN_오늘_요일_삭제_거부() {
    // given - OPEN + 오늘 요일 row 가 prev 에 있고 new 에서 빠짐 (휴무 전환)
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID))
        .willReturn(List.of(hourOf(s, today, LocalTime.of(9, 0), LocalTime.of(21, 0))));

    // when / then - new 는 빈 list
    assertThatThrownBy(() -> storeService.saveBusinessHours(SELLER_ID, STORE_ID, List.of()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.TODAY_BUSINESS_HOURS_LOCKED);
  }

  @Test
  void 영업시간_저장_OPEN_오늘_요일_신규_추가_허용() {
    // given - OPEN + 오늘 요일 prev 에 없음, new 에 추가 → 허용
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID)).willReturn(List.of());

    List<BusinessHourPayload> req =
        List.of(payload(today, LocalTime.of(9, 0), LocalTime.of(21, 0)));

    // when
    List<BusinessHourPayload> result = storeService.saveBusinessHours(SELLER_ID, STORE_ID, req);

    // then
    assertThat(result).hasSize(1);
  }

  @Test
  void 영업시간_저장_OPEN_다른_요일_변경_허용() {
    // given - OPEN + 오늘 요일 row 동일 유지, 다른 요일 변경 → 허용
    Store s = store(STORE_ID, seller());
    s.changeOperationStatus(OperationStatus.OPEN);
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    DayOfWeek otherDay = today.plus(1);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID))
        .willReturn(
            List.of(
                hourOf(s, today, LocalTime.of(9, 0), LocalTime.of(21, 0)),
                hourOf(s, otherDay, LocalTime.of(9, 0), LocalTime.of(21, 0))));

    List<BusinessHourPayload> req =
        List.of(
            payload(today, LocalTime.of(9, 0), LocalTime.of(21, 0)), // 오늘 동일
            payload(otherDay, LocalTime.of(10, 0), LocalTime.of(22, 0))); // 다른 요일 변경

    // when
    List<BusinessHourPayload> result = storeService.saveBusinessHours(SELLER_ID, STORE_ID, req);

    // then
    assertThat(result).hasSize(2);
  }

  @Test
  void 영업시간_저장_CLOSED_TODAY_상태에서는_오늘_변경_허용() {
    // given - CLOSED_TODAY 면 오늘 변경도 자유
    Store s = store(STORE_ID, seller()); // 초기 CLOSED_TODAY
    DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeBusinessHourRepository.findByStoreId(STORE_ID))
        .willReturn(List.of(hourOf(s, today, LocalTime.of(9, 0), LocalTime.of(21, 0))));

    List<BusinessHourPayload> req =
        List.of(payload(today, LocalTime.of(10, 0), LocalTime.of(22, 0)));

    // when - 거부 없이 통과
    List<BusinessHourPayload> result = storeService.saveBusinessHours(SELLER_ID, STORE_ID, req);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).openTime()).isEqualTo(LocalTime.of(10, 0));
  }

  @Test
  void 영업시간_저장_소유권_없음_예외() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> storeService.saveBusinessHours(SELLER_ID, STORE_ID, List.of()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
  }

  // ── 매장 정보 수정 ──────────────────────────────────────────────────────────

  private StoreUpdateRequest updateOf(
      String name,
      String roadAddress,
      String jibunAddress,
      String detailAddress,
      String zonecode,
      String phone,
      String description) {
    return new StoreUpdateRequest(
        name, roadAddress, jibunAddress, detailAddress, zonecode, phone, description);
  }

  private StoreUpdateRequest emptyUpdate() {
    return new StoreUpdateRequest(null, null, null, null, null, null, null);
  }

  private StoreDetailResponse stubDetail() {
    return new StoreDetailResponse(
        STORE_ID,
        "1234567890",
        "동네빵집",
        "서울 강남구",
        null,
        null,
        "06158",
        37.5,
        127.0,
        "0212345678",
        "소개",
        "/uploads/2026/5/uuid.jpg",
        OffsetDateTime.now());
  }

  @Test
  void 매장_정보_수정_성공_매장명만_변경() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    StoreUpdateRequest req = updateOf("새이름", null, null, null, null, null, null);

    // when
    StoreDetailResponse result = storeService.updateStore(SELLER_ID, STORE_ID, req, null);

    // then
    assertThat(result).isNotNull();
    assertThat(s.getName()).isEqualTo("새이름");
    then(geocodingService).should(never()).geocode(any());
    then(storageService).should(never()).upload(any());
    then(storageService).should(never()).delete(any());
  }

  @Test
  void 매장_정보_수정_주소_변경시_지오코딩_재호출() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(geocodingService.geocode("새 주소")).willReturn(GeometryUtil.toPoint(37.6, 127.1));
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    StoreUpdateRequest req = updateOf(null, "새 주소", "지번 새", null, "12345", null, null);

    // when
    storeService.updateStore(SELLER_ID, STORE_ID, req, null);

    // then
    assertThat(s.getRoadAddress()).isEqualTo("새 주소");
    assertThat(s.getJibunAddress()).isEqualTo("지번 새");
    assertThat(s.getZonecode()).isEqualTo("12345");
    then(geocodingService).should().geocode("새 주소");
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 매장_정보_수정_주소_동일하면_지오코딩_호출_안함() {
    // given - request.roadAddress == prev
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    StoreUpdateRequest req =
        updateOf(null, "서울 강남구 테헤란로 427", null, null, null, null, null); // store() helper 와 동일

    // when
    storeService.updateStore(SELLER_ID, STORE_ID, req, null);

    // then
    then(geocodingService).should(never()).geocode(any());
  }

  @Test
  void 매장_정보_수정_사진_변경시_업로드_및_기존_삭제() {
    // given
    Store s = store(STORE_ID, seller()); // imageUrl = /uploads/2026/5/uuid.jpg
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storageService.upload(any())).willReturn("/uploads/2026/6/new.jpg");
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    MockMultipartFile newImage =
        new MockMultipartFile("image", "new.jpg", "image/jpeg", new byte[1024]);

    // when
    storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), newImage);

    // then
    assertThat(s.getImageUrl()).isEqualTo("/uploads/2026/6/new.jpg");
    then(storageService).should().upload(any());
    then(storageService).should().delete("/uploads/2026/5/uuid.jpg"); // best effort
    then(geocodingService).should(never()).geocode(any());
  }

  @Test
  void 매장_정보_수정_사진_미변경_업로드_및_삭제_없음() {
    // given - image = null
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    // when
    storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), null);

    // then
    then(storageService).should(never()).upload(any());
    then(storageService).should(never()).delete(any());
  }

  @Test
  void 매장_정보_수정_지오코딩_실패_DB_변경_없음() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(geocodingService.geocode(any()))
        .willThrow(new BusinessException(StoreErrorCode.ADDRESS_GEOCODING_FAILED));

    StoreUpdateRequest req = updateOf(null, "새 주소", null, null, "12345", null, null);

    // when / then
    assertThatThrownBy(() -> storeService.updateStore(SELLER_ID, STORE_ID, req, null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.ADDRESS_GEOCODING_FAILED);
    then(storageService).should(never()).upload(any());
    then(storeRepository).should(never()).save(any());
  }

  @Test
  void 매장_정보_수정_사진_업로드_실패_DB_변경_없음() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storageService.upload(any()))
        .willThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR));

    MockMultipartFile newImage =
        new MockMultipartFile("image", "new.jpg", "image/jpeg", new byte[1024]);

    // when / then
    assertThatThrownBy(() -> storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), newImage))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_IMAGE_UPLOAD_FAILED);
    then(storeRepository).should(never()).save(any());
    then(storageService).should(never()).delete(any());
  }

  @Test
  void 매장_정보_수정_파일_크기_초과_예외() {
    // given
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));

    MockMultipartFile bigFile =
        new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);

    // when / then
    assertThatThrownBy(() -> storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), bigFile))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_IMAGE_TOO_LARGE);
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 매장_정보_수정_기존사진_삭제_실패해도_업데이트_성공() {
    // given - delete 가 RuntimeException 던져도 흐름 진행 (best effort)
    Store s = store(STORE_ID, seller());
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storageService.upload(any())).willReturn("/uploads/new.jpg");
    willThrow(new RuntimeException("storage unreachable")).given(storageService).delete(any());
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    MockMultipartFile newImage =
        new MockMultipartFile("image", "new.jpg", "image/jpeg", new byte[1024]);

    // when - 예외 없이 통과
    StoreDetailResponse result =
        storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), newImage);

    // then
    assertThat(result).isNotNull();
    then(storeRepository).should().save(any(Store.class));
  }

  @Test
  void 매장_정보_수정_모든_필드_null_변경_없음_save_호출() {
    // given - request 모두 null + image null → 변경 없지만 save 는 호출 (단순 흐름)
    Store s = store(STORE_ID, seller());
    String prevName = s.getName();
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeRepository.save(any(Store.class))).willReturn(s);
    given(storeMapper.toDetailResponse(any(Store.class))).willReturn(stubDetail());

    // when
    storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), null);

    // then
    assertThat(s.getName()).isEqualTo(prevName);
    then(geocodingService).should(never()).geocode(any());
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 매장_정보_수정_소유권_없음_예외() {
    // given
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> storeService.updateStore(SELLER_ID, STORE_ID, emptyUpdate(), null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ACCESS_DENIED);
    then(geocodingService).should(never()).geocode(any());
    then(storageService).should(never()).upload(any());
  }
}
