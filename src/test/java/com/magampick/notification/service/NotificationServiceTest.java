package com.magampick.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.PushToken;
import com.magampick.notification.repository.PushTokenRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock PushTokenRepository pushTokenRepository;
  @Mock FcmSender fcmSender;
  @InjectMocks NotificationService notificationService;

  private static final Long OWNER_ID = 1L;

  @Test
  void 발송_대상_토큰_없으면_0_반환하고_발송_안함() {
    // given
    given(pushTokenRepository.findByOwnerTypeAndOwnerId(Role.CUSTOMER, OWNER_ID))
        .willReturn(List.of());

    // when
    int sent = notificationService.sendToOwner(Role.CUSTOMER, OWNER_ID, "제목", "본문");

    // then
    assertThat(sent).isZero();
    then(fcmSender).should(never()).sendEachToTokens(any(), any(), any());
  }

  @Test
  void 토큰들로_발송하고_성공수_반환_죽은토큰_없으면_정리_안함() {
    // given
    given(pushTokenRepository.findByOwnerTypeAndOwnerId(Role.CUSTOMER, OWNER_ID))
        .willReturn(List.of(token("t1"), token("t2")));
    given(fcmSender.sendEachToTokens(List.of("t1", "t2"), "제목", "본문"))
        .willReturn(new FcmSendResult(2, List.of()));

    // when
    int sent = notificationService.sendToOwner(Role.CUSTOMER, OWNER_ID, "제목", "본문");

    // then
    assertThat(sent).isEqualTo(2);
    then(pushTokenRepository).should(never()).deleteByTokenIn(any());
  }

  @Test
  void 죽은_토큰은_정리한다() {
    // given — t2 가 죽은 토큰
    given(pushTokenRepository.findByOwnerTypeAndOwnerId(Role.SELLER, OWNER_ID))
        .willReturn(List.of(token("t1"), token("t2")));
    given(fcmSender.sendEachToTokens(List.of("t1", "t2"), "제목", "본문"))
        .willReturn(new FcmSendResult(1, List.of("t2")));

    // when
    int sent = notificationService.sendToOwner(Role.SELLER, OWNER_ID, "제목", "본문");

    // then
    assertThat(sent).isEqualTo(1);
    then(pushTokenRepository).should().deleteByTokenIn(List.of("t2"));
  }

  private PushToken token(String value) {
    return PushToken.builder()
        .ownerType(Role.CUSTOMER)
        .ownerId(OWNER_ID)
        .token(value)
        .platform(PushToken.Platform.WEB)
        .build();
  }
}
