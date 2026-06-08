package com.magampick.notification.service;

import com.magampick.notification.domain.SellerNotificationSetting;
import com.magampick.notification.dto.SellerNotificationSettingsResponse;
import com.magampick.notification.repository.SellerNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사장 알림 수신 설정 관리. 가입 시 기본값 생성, 항목별 on/off 갱신. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerNotificationSettingService {

  private final SellerNotificationSettingRepository settingRepository;

  /** 사장 알림 설정 조회. 설정이 없으면 IllegalStateException (가입 시 생성이므로 항상 있어야 함). */
  public SellerNotificationSettingsResponse getSettings(Long sellerId) {
    SellerNotificationSetting setting = requireSetting(sellerId);
    return SellerNotificationSettingsResponse.from(setting);
  }

  /** 특정 알림 설정 키 변경. */
  @Transactional
  public SellerNotificationSettingsResponse updateSetting(
      Long sellerId, String key, boolean enabled) {
    SellerNotificationSetting setting = requireSetting(sellerId);
    setting.updateKey(key, enabled);
    log.info("사장 알림 설정 변경됨. sellerId={}, key={}, enabled={}", sellerId, key, enabled);
    return SellerNotificationSettingsResponse.from(setting);
  }

  /** 가입 시 기본 알림 설정 생성. */
  @Transactional
  public void createDefault(Long sellerId) {
    settingRepository.save(SellerNotificationSetting.defaultFor(sellerId));
    log.info("사장 기본 알림 설정 생성됨. sellerId={}", sellerId);
  }

  private SellerNotificationSetting requireSetting(Long sellerId) {
    return settingRepository
        .findBySellerId(sellerId)
        .orElseThrow(
            () ->
                new IllegalStateException("알림 설정이 없습니다. sellerId=" + sellerId + " — 가입 시 생성됩니다."));
  }
}
