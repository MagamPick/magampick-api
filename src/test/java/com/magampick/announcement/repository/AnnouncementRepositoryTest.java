package com.magampick.announcement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.announcement.domain.Announcement;
import com.magampick.announcement.domain.NoticeTag;
import com.magampick.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 공지사항 Repository 정렬 쿼리 검증. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AnnouncementRepositoryTest {

  @Autowired AnnouncementRepository announcementRepository;

  @Test
  void 정렬_핀_우선_발행일_최신_id_내림차순() {
    // given
    // 핀 없음, 오래된 날짜
    Announcement older =
        Announcement.builder()
            .tag(NoticeTag.NOTICE)
            .pinned(false)
            .title("오래된 공지")
            .body("본문1")
            .publishedAt(LocalDate.of(2025, 1, 1))
            .build();

    // 핀 없음, 최신 날짜
    Announcement newer =
        Announcement.builder()
            .tag(NoticeTag.EVENT)
            .pinned(false)
            .title("최신 이벤트")
            .body("본문2")
            .publishedAt(LocalDate.of(2026, 6, 9))
            .build();

    // 핀 있음, 중간 날짜 → 핀이 있으므로 맨 앞에 와야 함
    Announcement pinned =
        Announcement.builder()
            .tag(NoticeTag.UPDATE)
            .pinned(true)
            .title("핀된 업데이트")
            .body("본문3")
            .publishedAt(LocalDate.of(2026, 5, 26))
            .build();

    announcementRepository.save(older);
    announcementRepository.save(newer);
    announcementRepository.save(pinned);

    // when
    List<Announcement> result =
        announcementRepository.findAllByOrderByPinnedDescPublishedAtDescIdDesc();

    // then: 저장된 3개 + seed 11개 포함될 수 있으므로 앞쪽 항목의 순서만 검증
    assertThat(result).hasSizeGreaterThanOrEqualTo(3);
    // 핀된 것이 가장 앞에 위치 (seed 데이터에도 핀된 것이 있을 수 있으나 published_at 이 특이한 것 확인)
    // 내가 저장한 3개 중에서의 상대 순서 검증
    List<Announcement> mySaved =
        result.stream()
            .filter(
                a ->
                    a.getTitle().equals("오래된 공지")
                        || a.getTitle().equals("최신 이벤트")
                        || a.getTitle().equals("핀된 업데이트"))
            .toList();

    assertThat(mySaved).hasSize(3);
    // 핀된 것이 첫 번째
    assertThat(mySaved.get(0).isPinned()).isTrue();
    assertThat(mySaved.get(0).getTitle()).isEqualTo("핀된 업데이트");
    // 핀 없는 것 중 최신 날짜가 두 번째
    assertThat(mySaved.get(1).isPinned()).isFalse();
    assertThat(mySaved.get(1).getTitle()).isEqualTo("최신 이벤트");
    // 가장 오래된 것이 마지막
    assertThat(mySaved.get(2).getTitle()).isEqualTo("오래된 공지");
  }

  @Test
  void 동일_발행일_동일_핀_id_내림차순() {
    // given: pinned=false, publishedAt 동일 → id 내림차순
    LocalDate sameDate = LocalDate.of(2024, 1, 1); // seed 데이터와 겹치지 않는 날짜

    Announcement a1 =
        Announcement.builder()
            .tag(NoticeTag.NOTICE)
            .pinned(false)
            .title("첫 번째 저장 ID테스트")
            .body("본문")
            .publishedAt(sameDate)
            .build();

    Announcement a2 =
        Announcement.builder()
            .tag(NoticeTag.NOTICE)
            .pinned(false)
            .title("두 번째 저장 ID테스트")
            .body("본문")
            .publishedAt(sameDate)
            .build();

    Announcement saved1 = announcementRepository.save(a1);
    Announcement saved2 = announcementRepository.save(a2);

    // when
    List<Announcement> result =
        announcementRepository.findAllByOrderByPinnedDescPublishedAtDescIdDesc();

    // then: 내가 저장한 두 개 중에서 id 내림차순 → saved2 가 앞에
    List<Announcement> myTwo =
        result.stream()
            .filter(
                a -> a.getTitle().equals("첫 번째 저장 ID테스트") || a.getTitle().equals("두 번째 저장 ID테스트"))
            .toList();

    assertThat(myTwo).hasSize(2);
    assertThat(myTwo.get(0).getId()).isGreaterThan(myTwo.get(1).getId());
    assertThat(myTwo.get(0).getId()).isEqualTo(saved2.getId());
    assertThat(myTwo.get(1).getId()).isEqualTo(saved1.getId());
  }
}
