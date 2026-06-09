package com.magampick.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.announcement.domain.Announcement;
import com.magampick.announcement.domain.NoticeTag;
import com.magampick.announcement.dto.AdminAnnouncementCreateRequest;
import com.magampick.announcement.dto.AdminAnnouncementUpdateRequest;
import com.magampick.announcement.dto.AnnouncementResponse;
import com.magampick.announcement.exception.AnnouncementErrorCode;
import com.magampick.announcement.fixture.AnnouncementFixture;
import com.magampick.announcement.mapper.AnnouncementMapper;
import com.magampick.announcement.repository.AnnouncementRepository;
import com.magampick.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

  @Mock AnnouncementRepository announcementRepository;
  @Mock AnnouncementMapper announcementMapper;

  // 2026-06-09 KST 고정 Clock
  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  @InjectMocks AnnouncementService announcementService;

  private void injectClock() {
    ReflectionTestUtils.setField(announcementService, "clock", fixedClock);
  }

  // ── list ─────────────────────────────────────────────────────────────────────

  @Test
  void 목록_조회_핀_우선_최신순() {
    // given
    Announcement pinned = AnnouncementFixture.aPinnedAnnouncement();
    Announcement normal = AnnouncementFixture.anAnnouncement();
    AnnouncementResponse pinnedResponse = AnnouncementFixture.aPinnedResponse();
    AnnouncementResponse normalResponse = AnnouncementFixture.aResponse();

    given(announcementRepository.findAllByOrderByPinnedDescPublishedAtDescIdDesc())
        .willReturn(List.of(pinned, normal));
    given(announcementMapper.toResponse(pinned)).willReturn(pinnedResponse);
    given(announcementMapper.toResponse(normal)).willReturn(normalResponse);

    // when
    List<AnnouncementResponse> result = announcementService.list();

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).pinned()).isTrue();
    assertThat(result.get(1).pinned()).isFalse();
    then(announcementRepository).should().findAllByOrderByPinnedDescPublishedAtDescIdDesc();
  }

  @Test
  void 목록_조회_빈_결과() {
    // given
    given(announcementRepository.findAllByOrderByPinnedDescPublishedAtDescIdDesc())
        .willReturn(List.of());

    // when
    List<AnnouncementResponse> result = announcementService.list();

    // then
    assertThat(result).isEmpty();
  }

  // ── create ───────────────────────────────────────────────────────────────────

  @Test
  void 생성_성공_발행일은_오늘() {
    // given
    injectClock();
    AdminAnnouncementCreateRequest req = AnnouncementFixture.aCreateRequest();
    Announcement saved = AnnouncementFixture.anAnnouncement();
    AnnouncementResponse expectedResponse = AnnouncementFixture.aResponse();

    given(announcementRepository.save(any(Announcement.class))).willReturn(saved);
    given(announcementMapper.toResponse(saved)).willReturn(expectedResponse);

    // when
    AnnouncementResponse result = announcementService.create(req);

    // then
    assertThat(result).isEqualTo(expectedResponse);
    // 저장 시 publishedAt = clock 기준 오늘 (2026-06-09 KST)
    then(announcementRepository)
        .should()
        .save(
            argThat(
                a ->
                    a.getPublishedAt().equals(LocalDate.of(2026, 6, 9))
                        && a.getTag() == req.tag()
                        && a.getTitle().equals(req.title())));
  }

  // ── update ───────────────────────────────────────────────────────────────────

  @Test
  void 수정_성공_null_필드는_유지() {
    // given
    Announcement announcement = AnnouncementFixture.anAnnouncement();
    AdminAnnouncementUpdateRequest req =
        new AdminAnnouncementUpdateRequest(NoticeTag.EVENT, null, "새 제목", null);
    AnnouncementResponse expectedResponse = AnnouncementFixture.aResponse();

    given(announcementRepository.findById(1L)).willReturn(Optional.of(announcement));
    given(announcementMapper.toResponse(announcement)).willReturn(expectedResponse);

    // when
    AnnouncementResponse result = announcementService.update(1L, req);

    // then
    assertThat(result).isEqualTo(expectedResponse);
    // tag, title 변경됨; pinned, body 는 null 이어서 유지됨
    assertThat(announcement.getTag()).isEqualTo(NoticeTag.EVENT);
    assertThat(announcement.getTitle()).isEqualTo("새 제목");
    assertThat(announcement.getBody()).isEqualTo("서버 정기 점검으로 해당 시간 동안 서비스가 제한될 수 있어요.");
  }

  @Test
  void 수정_없는_id_ANNOUNCEMENT_NOT_FOUND() {
    // given
    given(announcementRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                announcementService.update(
                    999L, new AdminAnnouncementUpdateRequest(null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND);
  }

  // ── delete ───────────────────────────────────────────────────────────────────

  @Test
  void 삭제_성공() {
    // given
    Announcement announcement = AnnouncementFixture.anAnnouncement();
    given(announcementRepository.findById(1L)).willReturn(Optional.of(announcement));

    // when
    announcementService.delete(1L);

    // then
    then(announcementRepository).should().delete(announcement);
  }

  @Test
  void 삭제_없는_id_ANNOUNCEMENT_NOT_FOUND() {
    // given
    given(announcementRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> announcementService.delete(999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND);
  }

  /** Mockito argThat 헬퍼 (람다). */
  private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
    return org.mockito.ArgumentMatchers.argThat(matcher);
  }
}
