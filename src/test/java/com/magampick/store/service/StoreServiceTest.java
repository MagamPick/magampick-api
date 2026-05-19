package com.magampick.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.storage.StorageService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.config.StoreProperties;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreCategory;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.mapper.StoreMapper;
import com.magampick.store.repository.StoreCategoryRepository;
import com.magampick.store.repository.StoreRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

  @Mock StoreRepository storeRepository;
  @Mock StoreCategoryRepository storeCategoryRepository;
  @Mock SellerRepository sellerRepository;
  @Mock StorageService storageService;
  @Mock BusinessVerificationService businessVerificationService;
  @Mock StoreMapper storeMapper;
  @Spy StoreProperties storeProperties = new StoreProperties(true);
  @InjectMocks StoreService storeService;

  private static final Long SELLER_ID = 1L;
  private static final Long STORE_ID = 10L;

  private Seller seller() {
    Seller s =
        Seller.builder()
            .email("seller@test.com")
            .passwordHash("hash")
            .ownerName("홍길동")
            .businessNumber("1234567890")
            .build();
    ReflectionTestUtils.setField(s, "id", SELLER_ID);
    return s;
  }

  private StoreCategory category(Long id, String name) {
    StoreCategory c = StoreCategory.builder().name(name).build();
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  private Store store(Long id, Seller seller, StoreStatus status) {
    Store s =
        Store.builder()
            .seller(seller)
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .imageUrl("/uploads/2026/5/uuid.jpg")
            .status(status)
            .categories(List.of(category(1L, "베이커리")))
            .build();
    ReflectionTestUtils.setField(s, "id", id);
    return s;
  }

  private StoreCreateRequest createRequest() {
    return new StoreCreateRequest(
        "동네빵집",
        "서울 강남구 테헤란로 427",
        null,
        null,
        "06158",
        37.5,
        127.0,
        "0212345678",
        "신선한 빵",
        List.of(1L));
  }

  private MockMultipartFile validImage() {
    return new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[1024]);
  }

  // ── 매장 등록 ──────────────────────────────────────────────────────────────

  @Test
  void 매장_등록_성공_자동승인() {
    // given
    StoreProperties autoApprove = new StoreProperties(true);
    ReflectionTestUtils.setField(storeService, "storeProperties", autoApprove);
    Seller seller = seller();
    StoreCategory cat = category(1L, "베이커리");
    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
    given(storeCategoryRepository.findAllById(List.of(1L))).willReturn(List.of(cat));
    given(storageService.upload(any())).willReturn("/uploads/2026/5/uuid.jpg");
    given(storeRepository.save(any(Store.class)))
        .willAnswer(
            inv -> {
              Store s = inv.getArgument(0);
              ReflectionTestUtils.setField(s, "id", STORE_ID);
              return s;
            });

    // when
    StoreRegisterResponse response =
        storeService.registerStore(SELLER_ID, createRequest(), validImage());

    // then
    assertThat(response.storeId()).isEqualTo(STORE_ID);
    assertThat(response.status()).isEqualTo(StoreStatus.APPROVED);
    then(businessVerificationService).should().verify("1234567890");
    then(storageService).should().upload(any());
    then(storeRepository).should().save(any(Store.class));
  }

  @Test
  void 매장_등록_성공_PENDING() {
    // given
    ReflectionTestUtils.setField(storeService, "storeProperties", new StoreProperties(false));
    Seller seller = seller();
    StoreCategory cat = category(1L, "베이커리");
    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
    given(storeCategoryRepository.findAllById(List.of(1L))).willReturn(List.of(cat));
    given(storageService.upload(any())).willReturn("/uploads/2026/5/uuid.jpg");
    given(storeRepository.save(any(Store.class)))
        .willAnswer(
            inv -> {
              Store s = inv.getArgument(0);
              ReflectionTestUtils.setField(s, "id", STORE_ID);
              return s;
            });

    // when
    StoreRegisterResponse response =
        storeService.registerStore(SELLER_ID, createRequest(), validImage());

    // then
    assertThat(response.status()).isEqualTo(StoreStatus.PENDING);
  }

  @Test
  void 매장_등록_카테고리_없음_예외() {
    // given
    Seller seller = seller();
    given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
    given(storeCategoryRepository.findAllById(List.of(1L))).willReturn(List.of()); // 0개 반환

    // when / then
    assertThatThrownBy(() -> storeService.registerStore(SELLER_ID, createRequest(), validImage()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_CATEGORY_NOT_FOUND);
    then(storageService).should(never()).upload(any());
  }

  @Test
  void 매장_등록_파일_크기_초과_예외() {
    // given
    MockMultipartFile bigFile =
        new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[6 * 1024 * 1024]); // 6MB

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

  // ── 사장 조회 ──────────────────────────────────────────────────────────────

  @Test
  void 본인_매장_목록_조회_성공() {
    // given
    Seller seller = seller();
    Store s = store(STORE_ID, seller, StoreStatus.APPROVED);
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
                StoreStatus.APPROVED,
                List.of("베이커리"),
                OffsetDateTime.now()));

    // when
    List<StoreResponse> result = storeService.getMyStores(SELLER_ID);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(STORE_ID);
  }

  @Test
  void 본인_매장_상세_조회_성공() {
    // given
    Seller seller = seller();
    Store s = store(STORE_ID, seller, StoreStatus.APPROVED);
    given(storeRepository.findByIdAndSellerId(STORE_ID, SELLER_ID)).willReturn(Optional.of(s));
    given(storeMapper.toDetailResponse(s))
        .willReturn(
            new StoreDetailResponse(
                STORE_ID,
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
                StoreStatus.APPROVED,
                null,
                List.of("베이커리"),
                OffsetDateTime.now()));

    // when
    StoreDetailResponse result = storeService.getMyStore(SELLER_ID, STORE_ID);

    // then
    assertThat(result.id()).isEqualTo(STORE_ID);
    assertThat(result.status()).isEqualTo(StoreStatus.APPROVED);
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

  // ── 관리자 승인/반려 ────────────────────────────────────────────────────────

  @Test
  void 관리자_매장_승인_성공() {
    // given
    Seller seller = seller();
    Store s = store(STORE_ID, seller, StoreStatus.PENDING);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(s));

    // when
    storeService.approveStore(STORE_ID);

    // then
    assertThat(s.getStatus()).isEqualTo(StoreStatus.APPROVED);
  }

  @Test
  void 관리자_매장_반려_성공() {
    // given
    Seller seller = seller();
    Store s = store(STORE_ID, seller, StoreStatus.PENDING);
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(s));

    // when
    storeService.rejectStore(STORE_ID, "사업자번호 불일치");

    // then
    assertThat(s.getStatus()).isEqualTo(StoreStatus.REJECTED);
    assertThat(s.getRejectionReason()).isEqualTo("사업자번호 불일치");
  }

  @Test
  void 관리자_이미_심사된_매장_승인_예외() {
    // given
    Seller seller = seller();
    Store s = store(STORE_ID, seller, StoreStatus.APPROVED); // 이미 승인
    given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(s));

    // when / then
    assertThatThrownBy(() -> storeService.approveStore(STORE_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", StoreErrorCode.STORE_ALREADY_REVIEWED);
  }
}
