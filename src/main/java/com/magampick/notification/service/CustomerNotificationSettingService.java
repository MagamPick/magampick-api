package com.magampick.notification.service;

import com.magampick.notification.domain.CustomerNotificationSetting;
import com.magampick.notification.dto.CustomerNotificationSettingsResponse;
import com.magampick.notification.repository.CustomerNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 알림 수신 설정 관리. 가입 시 기본값 생성, 항목별 on/off 갱신. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerNotificationSettingService {

  private final CustomerNotificationSettingRepository settingRepository;

  /** 소비자 알림 설정 조회. 설정이 없으면 IllegalStateException (가입 시 생성이므로 항상 있어야 함). */
  public CustomerNotificationSettingsResponse getSettings(Long customerId) {
    CustomerNotificationSetting setting = requireSetting(customerId);
    return CustomerNotificationSettingsResponse.from(setting);
  }

  /** 특정 알림 설정 키 변경. */
  @Transactional
  public CustomerNotificationSettingsResponse updateSetting(
      Long customerId, String key, boolean enabled) {
    // 설정 조회
    CustomerNotificationSetting setting = requireSetting(customerId);
    // 설정 키 변경
    setting.updateKey(key, enabled);
    log.info("소비자 알림 설정 변경됨. customerId={}, key={}, enabled={}", customerId, key, enabled);
    return CustomerNotificationSettingsResponse.from(setting);
  }

  /** 가입 시 기본 알림 설정 생성. */
  @Transactional
  public void createDefault(Long customerId) {
    settingRepository.save(CustomerNotificationSetting.defaultFor(customerId));
    log.info("소비자 기본 알림 설정 생성됨. customerId={}", customerId);
  }

  private CustomerNotificationSetting requireSetting(Long customerId) {
    return settingRepository
        .findByCustomerId(customerId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "알림 설정이 없습니다. customerId=" + customerId + " — 가입 시 생성됩니다."));
  }
}
