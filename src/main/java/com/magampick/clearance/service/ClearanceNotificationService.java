package com.magampick.clearance.service;

import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 떨이 관련 소비자 알림: 떨이 등록 알림 + 마감 임박 알림. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClearanceNotificationService {

  private static final double NEARBY_METERS = 3000.0;

  private final FavoriteRepository favoriteRepository;
  private final AddressRepository addressRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final NotificationService notificationService;

  /**
   * 떨이 등록 시 관련 소비자 알림 발송. 우선순위: 즐겨찾기 → 기본 주소지 3km. 중복 시 즐겨찾기 우선.
   *
   * @param item 등록된 떨이 상품
   */
  @Transactional
  public void notifyNewClearanceItem(ClearanceItem item) {
    // 매장 및 상품 정보 추출
    Long storeId = item.getStore().getId();
    double lat = GeometryUtil.latitude(item.getStore().getLocation());
    double lng = GeometryUtil.longitude(item.getStore().getLocation());
    String storeName = item.getStore().getName();
    String itemName = item.getName();

    // 알림 대상자 조회
    List<Long> favoriteCustomerIds = favoriteRepository.findCustomerIdsByStoreId(storeId);
    List<Long> nearbyCustomerIds =
        addressRepository.findCustomerIdsWithDefaultAddressNear(lat, lng, NEARBY_METERS);

    // 대상자 합산 (우선순위: 즐겨찾기 > 주소지, 중복 제거)
    Map<Long, String> targets = new LinkedHashMap<>();
    for (Long cid : favoriteCustomerIds) {
      targets.put(cid, "favoriteStore");
    }
    for (Long cid : nearbyCustomerIds) {
      targets.putIfAbsent(cid, "nearbyDeal");
    }

    // 알림 발송
    for (Map.Entry<Long, String> entry : targets.entrySet()) {
      Long customerId = entry.getKey();
      String settingKey = entry.getValue();
      String title =
          "favoriteStore".equals(settingKey) ? "단골 가게의 새 마감 할인!" : "근처 가게에 마감 할인이 등록됐어요!";
      String body = storeName + "에 " + itemName + "이(가) 등록됐어요.";
      try {
        notificationService.notifyCustomer(
            customerId, settingKey, NotificationCategory.DEAL, title, body, "/");
      } catch (Exception e) {
        log.warn("떨이 등록 알림 발송 실패. customerId={}, clearanceItemId={}", customerId, item.getId(), e);
      }
    }

    log.info("떨이 등록 알림 발송됨. clearanceItemId={}, 대상={}", item.getId(), targets.size());
  }

  /**
   * 마감 임박 알림 발송 (스케줄러 호출). OPEN + closingAlertSentAt IS NULL + pickupEndAt in [now+55min,
   * now+65min] 대상.
   *
   * @param now 현재 시각 (KST)
   */
  @Transactional
  public void sendClosingAlerts(LocalDateTime now) {
    // 알림 시간 범위 설정
    LocalDateTime from = now.plusMinutes(55);
    LocalDateTime to = now.plusMinutes(65);

    // 대상 떨이 조회
    List<ClearanceItem> targets =
        clearanceItemRepository.findAllByStatusAndClosingAlertSentAtIsNullAndPickupEndAtBetween(
            ClearanceItemStatus.OPEN, from, to);

    // 알림 발송
    for (ClearanceItem item : targets) {
      sendClosingAlert(item, now);
    }

    if (!targets.isEmpty()) {
      log.info("마감 임박 알림 발송됨. 대상 떨이 수={}", targets.size());
    }
  }

  private void sendClosingAlert(ClearanceItem item, LocalDateTime now) {
    // 매장 정보 추출
    Long storeId = item.getStore().getId();
    double lat = GeometryUtil.latitude(item.getStore().getLocation());
    double lng = GeometryUtil.longitude(item.getStore().getLocation());
    String storeName = item.getStore().getName();
    String itemName = item.getName();

    // 알림 대상자 조회
    List<Long> favoriteCustomerIds = favoriteRepository.findCustomerIdsByStoreId(storeId);
    List<Long> nearbyCustomerIds =
        addressRepository.findCustomerIdsWithDefaultAddressNear(lat, lng, NEARBY_METERS);

    // 대상자 합산
    Map<Long, String> targets = new LinkedHashMap<>();
    for (Long cid : favoriteCustomerIds) targets.put(cid, "favoriteStore");
    for (Long cid : nearbyCustomerIds) targets.putIfAbsent(cid, "nearbyDeal");

    // 알림 발송
    String title = "마감 1시간 전!";
    String body = storeName + "의 " + itemName + "이(가) 곧 마감돼요.";

    for (Map.Entry<Long, String> entry : targets.entrySet()) {
      try {
        notificationService.notifyCustomer(
            entry.getKey(), entry.getValue(), NotificationCategory.DEAL, title, body, "/");
      } catch (Exception e) {
        log.warn(
            "마감 임박 알림 발송 실패. customerId={}, clearanceItemId={}", entry.getKey(), item.getId(), e);
      }
    }

    // 발송 완료 처리
    item.markClosingAlertSent(now);
    clearanceItemRepository.save(item);
  }
}
