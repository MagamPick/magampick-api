package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.SellerNotificationSetting;
import com.magampick.notification.dto.SellerNotificationSettingsResponse;
import com.magampick.notification.exception.NotificationErrorCode;
import com.magampick.notification.repository.SellerNotificationSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerNotificationSettingServiceTest {

  @Mock SellerNotificationSettingRepository settingRepository;
  @InjectMocks SellerNotificationSettingService settingService;

  private SellerNotificationSetting defaultSetting(Long sellerId) {
    SellerNotificationSetting setting = SellerNotificationSetting.defaultFor(sellerId);
    ReflectionTestUtils.setField(setting, "id", 1L);
    return setting;
  }

  // ── getSettings ───────────────────────────────────────────────────────────────

  @Test
  void 알림_설정_조회_성공() {
    // given
    SellerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findBySellerId(1L)).willReturn(Optional.of(setting));

    // when
    SellerNotificationSettingsResponse response = settingService.getSettings(1L);

    // then
    assertThat(response.newOrder()).isTrue();
    assertThat(response.marketing()).isFalse();
  }

  @Test
  void 알림_설정_조회_실패_설정_미존재() {
    // given
    given(settingRepository.findBySellerId(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> settingService.getSettings(999L))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── updateSetting ─────────────────────────────────────────────────────────────

  @Test
  void 알림_설정_변경_성공_newOrder_false() {
    // given
    SellerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findBySellerId(1L)).willReturn(Optional.of(setting));

    // when
    SellerNotificationSettingsResponse response =
        settingService.updateSetting(1L, "newOrder", false);

    // then
    assertThat(response.newOrder()).isFalse();
    assertThat(setting.isNewOrder()).isFalse();
  }

  @Test
  void 알림_설정_변경_성공_marketing_true() {
    // given
    SellerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findBySellerId(1L)).willReturn(Optional.of(setting));

    // when
    SellerNotificationSettingsResponse response =
        settingService.updateSetting(1L, "marketing", true);

    // then
    assertThat(response.marketing()).isTrue();
  }

  @Test
  void 알림_설정_변경_실패_잘못된_키() {
    // given
    SellerNotificationSetting setting = defaultSetting(1L);
    given(settingRepository.findBySellerId(1L)).willReturn(Optional.of(setting));

    // when / then
    assertThatThrownBy(() -> settingService.updateSetting(1L, "badKey", true))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", NotificationErrorCode.INVALID_NOTIFICATION_SETTING_KEY);
  }

  @Test
  void 알림_설정_변경_실패_설정_미존재() {
    // given
    given(settingRepository.findBySellerId(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> settingService.updateSetting(999L, "newOrder", false))
        .isInstanceOf(IllegalStateException.class);
  }

  // ── createDefault ─────────────────────────────────────────────────────────────

  @Test
  void 기본_알림_설정_생성_성공() {
    // given
    given(settingRepository.save(any(SellerNotificationSetting.class)))
        .willAnswer(inv -> inv.getArgument(0));

    // when
    settingService.createDefault(1L);

    // then
    verify(settingRepository).save(any(SellerNotificationSetting.class));
  }
}
