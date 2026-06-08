package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.CustomerNotificationSetting;
import com.magampick.notification.dto.CustomerNotificationSettingsResponse;
import com.magampick.notification.exception.NotificationErrorCode;
import com.magampick.notification.repository.CustomerNotificationSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerNotificationSettingServiceTest {

  @Mock CustomerNotificationSettingRepository settingRepository;
  @InjectMocks CustomerNotificationSettingService settingService;

  private CustomerNotificationSetting defaultSetting(Long customerId) {
    CustomerNotificationSetting setting = CustomerNotificationSetting.defaultFor(customerId);
    ReflectionTestUtils.setField(setting, "id", 1L);
    return setting;
  }

  // ── getSettings ───────────────────────────────────────────────────────────────

  @Test
  void 알림_설정_조회_성공() {
    // given
    CustomerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findByCustomerId(1L)).willReturn(Optional.of(setting));

    // when
    CustomerNotificationSettingsResponse response = settingService.getSettings(1L);

    // then
    assertThat(response.nearbyDeal()).isTrue();
    assertThat(response.marketing()).isFalse();
  }

  @Test
  void 알림_설정_조회_실패_설정_미존재() {
    // given
    given(settingRepository.findByCustomerId(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> settingService.getSettings(999L))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── updateSetting ─────────────────────────────────────────────────────────────

  @Test
  void 알림_설정_변경_성공_nearbyDeal_false() {
    // given
    CustomerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findByCustomerId(1L)).willReturn(Optional.of(setting));

    // when
    CustomerNotificationSettingsResponse response =
        settingService.updateSetting(1L, "nearbyDeal", false);

    // then
    assertThat(response.nearbyDeal()).isFalse();
    assertThat(setting.isNearbyDeal()).isFalse();
  }

  @Test
  void 알림_설정_변경_성공_marketing_true() {
    // given
    CustomerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findByCustomerId(1L)).willReturn(Optional.of(setting));

    // when
    CustomerNotificationSettingsResponse response =
        settingService.updateSetting(1L, "marketing", true);

    // then
    assertThat(response.marketing()).isTrue();
  }

  @Test
  void 알림_설정_변경_실패_잘못된_키() {
    // given
    CustomerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findByCustomerId(1L)).willReturn(Optional.of(setting));

    // when / then
    assertThatThrownBy(() -> settingService.updateSetting(1L, "unknownKey", true))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", NotificationErrorCode.INVALID_NOTIFICATION_SETTING_KEY);
  }

  @Test
  void 알림_설정_변경_실패_설정_미존재() {
    // given
    given(settingRepository.findByCustomerId(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> settingService.updateSetting(999L, "nearbyDeal", false))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── createDefault ─────────────────────────────────────────────────────────────

  @Test
  void 기본_알림_설정_생성_성공() {
    // given
    given(settingRepository.save(any(CustomerNotificationSetting.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    settingService.createDefault(1L);

    // then
    verify(settingRepository).save(any(CustomerNotificationSetting.class));
  }
}
