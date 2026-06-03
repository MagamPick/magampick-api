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
import com.magampick.store.dto.BusinessVerificationRequest;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.mapper.StoreMapper;
import com.magampick.store.repository.StoreRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
}
