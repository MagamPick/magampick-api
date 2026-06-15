package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.PushToken;
import com.magampick.notification.dto.PushTokenResponse;
import com.magampick.notification.repository.PushTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

  @Mock PushTokenRepository pushTokenRepository;
  @InjectMocks PushTokenService pushTokenService;

  private static final Long OWNER_ID = 1L;
  private static final String TOKEN = "fcm-token-abc";

  @Test
  void 토큰_신규_등록시_저장하고_id_반환() {
    // given
    given(pushTokenRepository.findByToken(TOKEN)).willReturn(Optional.empty());
    given(pushTokenRepository.save(any(PushToken.class)))
        .willAnswer(
            inv -> {
              PushToken saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 100L);
              return saved;
            });

    // when
    PushTokenResponse response = pushTokenService.register(Role.CUSTOMER, OWNER_ID, TOKEN);

    // then
    assertThat(response.id()).isEqualTo(100L);
    then(pushTokenRepository).should().save(any(PushToken.class));
  }

  @Test
  void 이미_등록된_토큰_재등록시_소유자_재할당하고_저장하지_않음() {
    // given — 기존 토큰은 다른 사용자(SELLER 2) 소유
    PushToken existing =
        PushToken.builder()
            .ownerType(Role.SELLER)
            .ownerId(2L)
            .token(TOKEN)
            .platform(PushToken.Platform.WEB)
            .build();
    ReflectionTestUtils.setField(existing, "id", 50L);
    given(pushTokenRepository.findByToken(TOKEN)).willReturn(Optional.of(existing));

    // when — CUSTOMER 1 이 같은 토큰 등록
    PushTokenResponse response = pushTokenService.register(Role.CUSTOMER, OWNER_ID, TOKEN);

    // then — 소유자 재할당(dirty update), save 미호출
    assertThat(existing.getOwnerType()).isEqualTo(Role.CUSTOMER);
    assertThat(existing.getOwnerId()).isEqualTo(OWNER_ID);
    assertThat(response.id()).isEqualTo(50L);
    then(pushTokenRepository).should(never()).save(any());
  }

  @Test
  void 토큰_해제시_deleteByToken_호출() {
    // when
    pushTokenService.unregister(TOKEN);

    // then
    then(pushTokenRepository).should().deleteByToken(TOKEN);
  }
}
