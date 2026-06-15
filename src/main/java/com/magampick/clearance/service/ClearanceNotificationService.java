package com.magampick.clearance.service;

import com.magampick.address.repository.AddressRepository;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.customer.repository.CustomerLocationRepository;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final FavoriteRepository favoriteRepository;
  private final AddressRepository addressRepository;
  private final CustomerLocationRepository customerLocationRepository;
  private final ClearanceItemRepository clearanceItemRepository;
  private final NotificationService notificationService;

  /**
   * 떨이 등록 시 관련 소비자 알림 발송. 우선순위: ① 즐겨찾기 → ② 현재위치 3km → ③ 기본 주소지 3km. 중복 시 상위 순위 우선.
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

    // 알림 대상자 조회 (현재 시각 기준 신선도 판단)
    LocalDateTime now = LocalDateTime.now(KST);
    Map<Long, String> targets = resolveTargets(storeId, lat, lng, now);

    // 알림 발송
    for (Map.Entry<Long, String> entry : targets.entrySet()) {
      Long customerId = entry.getKey();
      String settingKey = entry.getValue();
      String title =
          "favoriteStore".equals(settingKey) ? "단골 가게의 새 마감 할인!" : "근처 가게에 마감 할인이 등록됐어요!";
      String body = storeName + "에 " + itemName + "이(가) 등록됐어요.";
      try {
        notificationService.notifyCustomer(
            customerId, settingKey, NotificationCategory.DEAL, title, body, "/store/" + storeId);
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
    Map<Long, String> targets = resolveTargets(storeId, lat, lng, now);

    // 알림 발송
    String title = "마감 1시간 전!";
    String body = storeName + "의 " + itemName + "이(가) 곧 마감돼요.";

    for (Map.Entry<Long, String> entry : targets.entrySet()) {
      try {
        notificationService.notifyCustomer(
            entry.getKey(),
            entry.getValue(),
            NotificationCategory.DEAL,
            title,
            body,
            "/store/" + storeId);
      } catch (Exception e) {
        log.warn(
            "마감 임박 알림 발송 실패. customerId={}, clearanceItemId={}", entry.getKey(), item.getId(), e);
      }
    }

    // 발송 완료 처리
    item.markClosingAlertSent(now);
    clearanceItemRepository.save(item);
  }

  /**
   * 알림 대상자 조회 + dedup. 우선순위: ① 즐겨찾기(favoriteStore) → ② 현재위치(nearbyDeal) → ③ 주소지(nearbyDeal).
   * LinkedHashMap 으로 삽입 순서 보존, putIfAbsent 로 상위 순위 보호.
   *
   * @param storeId 매장 ID
   * @param lat 매장 위도
   * @param lng 매장 경도
   * @param now 기준 시각 (신선도 판단: now - 1시간)
   * @return customerId → settingKey 매핑 (dedup 완료)
   */
  private Map<Long, String> resolveTargets(
      Long storeId, double lat, double lng, LocalDateTime now) {
    // ① 즐겨찾기
    List<Long> favoriteIds = favoriteRepository.findCustomerIdsByStoreId(storeId);
    // ② 현재위치 3km (1시간 이내 갱신된 소비자만)
    List<Long> currentLocationIds =
        customerLocationRepository.findCustomerIdsNear(lat, lng, NEARBY_METERS, now.minusHours(1));
    // ③ 기본 주소지 3km
    List<Long> defaultAddressIds =
        addressRepository.findCustomerIdsWithDefaultAddressNear(lat, lng, NEARBY_METERS);

    Map<Long, String> targets = new LinkedHashMap<>();
    for (Long cid : favoriteIds) targets.put(cid, "favoriteStore");
    for (Long cid : currentLocationIds) targets.putIfAbsent(cid, "nearbyDeal"); // ② 현재위치
    for (Long cid : defaultAddressIds) targets.putIfAbsent(cid, "nearbyDeal"); // ③ 주소지
    return targets;
  }
}
