package com.magampick.clearance.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.fixture.ClearanceItemFixture;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.seller.domain.Seller;
import com.magampick.store.domain.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClearanceNotificationServiceTest {

  @Mock FavoriteRepository favoriteRepository;
  @Mock AddressRepository addressRepository;
  @Mock ClearanceItemRepository clearanceItemRepository;
  @Mock NotificationService notificationService;
  @InjectMocks ClearanceNotificationService clearanceNotificationService;

  private static final Long STORE_ID = 10L;
  private static final Long CUSTOMER_ID_FAV = 1L;
  private static final Long CUSTOMER_ID_NEARBY = 2L;
  private static final Long CLEARANCE_ITEM_ID = 200L;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private Store store() {
    Seller seller =
        Seller.builder().email("seller@test.com").passwordHash("hash").ownerName("홍길동").build();
    ReflectionTestUtils.setField(seller, "id", 1L);
    Store s =
        Store.builder()
            .seller(seller)
            .businessNumber("1234567890")
            .representativeName("홍길동")
            .openDate(LocalDate.of(2024, 3, 15))
            .name("동네빵집")
            .roadAddress("서울 강남구 테헤란로 427")
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5, 127.0))
            .phone("0212345678")
            .imageUrl("/uploads/store.jpg")
            .build();
    ReflectionTestUtils.setField(s, "id", STORE_ID);
    return s;
  }

  private Product product() {
    Store s = store();
    Product p =
        Product.builder()
            .store(s)
            .name("크로아상")
            .regularPrice(new BigDecimal("4500"))
            .imageUrl("/uploads/product.jpg")
            .status(ProductStatus.ON_SALE)
            .build();
    ReflectionTestUtils.setField(p, "id", 100L);
    return p;
  }

  private ClearanceItem item() {
    ClearanceItem ci = ClearanceItemFixture.aClearanceItem(store(), product());
    ReflectionTestUtils.setField(ci, "id", CLEARANCE_ITEM_ID);
    return ci;
  }

  private LocalDateTime todayAt(int hour, int minute) {
    return LocalDate.now(KST).atTime(hour, minute);
  }

  // ── 떨이 등록 알림 ────────────────────────────────────────────────────────────

  @Test
  void 떨이_등록_알림_즐겨찾기_고객에게_favoriteStore_키로_발송() {
    // given
    ClearanceItem ci = item();
    given(favoriteRepository.findCustomerIdsByStoreId(STORE_ID))
        .willReturn(List.of(CUSTOMER_ID_FAV));
    given(addressRepository.findCustomerIdsWithDefaultAddressNear(37.5, 127.0, 3000.0))
        .willReturn(List.of());

    // when
    clearanceNotificationService.notifyNewClearanceItem(ci);

    // then
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID_FAV),
            eq("favoriteStore"),
            eq(NotificationCategory.DEAL),
            any(),
            any(),
            any());
  }

  @Test
  void 떨이_등록_알림_주소지_3km_이내_고객에게_nearbyDeal_키로_발송() {
    // given
    ClearanceItem ci = item();
    given(favoriteRepository.findCustomerIdsByStoreId(STORE_ID)).willReturn(List.of());
    given(addressRepository.findCustomerIdsWithDefaultAddressNear(37.5, 127.0, 3000.0))
        .willReturn(List.of(CUSTOMER_ID_NEARBY));

    // when
    clearanceNotificationService.notifyNewClearanceItem(ci);

    // then
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID_NEARBY),
            eq("nearbyDeal"),
            eq(NotificationCategory.DEAL),
            any(),
            any(),
            any());
  }

  @Test
  void 떨이_등록_알림_즐겨찾기_AND_주소지_중복_시_favoriteStore_우선() {
    // given — CUSTOMER_ID_FAV 가 즐겨찾기이면서 주소지 3km 이내에도 해당
    ClearanceItem ci = item();
    given(favoriteRepository.findCustomerIdsByStoreId(STORE_ID))
        .willReturn(List.of(CUSTOMER_ID_FAV));
    given(addressRepository.findCustomerIdsWithDefaultAddressNear(37.5, 127.0, 3000.0))
        .willReturn(List.of(CUSTOMER_ID_FAV, CUSTOMER_ID_NEARBY));

    // when
    clearanceNotificationService.notifyNewClearanceItem(ci);

    // then — CUSTOMER_ID_FAV 는 favoriteStore 키, CUSTOMER_ID_NEARBY 는 nearbyDeal 키
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID_FAV),
            eq("favoriteStore"),
            eq(NotificationCategory.DEAL),
            any(),
            any(),
            any());
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID_NEARBY),
            eq("nearbyDeal"),
            eq(NotificationCategory.DEAL),
            any(),
            any(),
            any());
    // 총 2회 — CUSTOMER_ID_FAV 는 nearbyDeal 로 중복 호출되지 않는다
    then(notificationService)
        .should(org.mockito.Mockito.times(2))
        .notifyCustomer(any(), any(), any(), any(), any(), any());
  }

  // ── 마감 임박 알림 ────────────────────────────────────────────────────────────

  @Test
  void 마감_임박_알림_pickupEndAt_60분전_OPEN_대상_발송() {
    // given
    LocalDateTime now = LocalDateTime.now(KST);
    LocalDateTime from = now.plusMinutes(55);
    LocalDateTime to = now.plusMinutes(65);
    ClearanceItem ci = item();
    given(
            clearanceItemRepository.findAllByStatusAndClosingAlertSentAtIsNullAndPickupEndAtBetween(
                ClearanceItemStatus.OPEN, from, to))
        .willReturn(List.of(ci));
    given(favoriteRepository.findCustomerIdsByStoreId(STORE_ID))
        .willReturn(List.of(CUSTOMER_ID_FAV));
    given(addressRepository.findCustomerIdsWithDefaultAddressNear(37.5, 127.0, 3000.0))
        .willReturn(List.of());

    // when
    clearanceNotificationService.sendClosingAlerts(now);

    // then
    then(notificationService)
        .should()
        .notifyCustomer(
            eq(CUSTOMER_ID_FAV),
            eq("favoriteStore"),
            eq(NotificationCategory.DEAL),
            any(),
            any(),
            any());
    then(clearanceItemRepository).should().save(ci);
  }

  @Test
  void 마감_임박_알림_이미_발송됨_closingAlertSentAt_있으면_skip() {
    // given — 레포지토리가 closingAlertSentAt IS NULL 필터를 적용하므로 결과 없음
    LocalDateTime now = LocalDateTime.now(KST);
    LocalDateTime from = now.plusMinutes(55);
    LocalDateTime to = now.plusMinutes(65);
    given(
            clearanceItemRepository.findAllByStatusAndClosingAlertSentAtIsNullAndPickupEndAtBetween(
                ClearanceItemStatus.OPEN, from, to))
        .willReturn(List.of());

    // when
    clearanceNotificationService.sendClosingAlerts(now);

    // then — 대상 없으므로 notifyCustomer 호출 없음
    then(notificationService)
        .should(never())
        .notifyCustomer(any(), any(), any(), any(), any(), any());
  }
}
