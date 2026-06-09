package com.magampick.announcement.fixture;

import com.magampick.announcement.domain.Announcement;
import com.magampick.announcement.domain.NoticeTag;
import com.magampick.announcement.dto.AdminAnnouncementCreateRequest;
import com.magampick.announcement.dto.AnnouncementResponse;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

/** 공지사항 도메인 테스트 픽스처. */
public class AnnouncementFixture {

  private AnnouncementFixture() {}

  /** 기본 공지사항 엔티티 (NOTICE, 비핀, 2026-06-09). */
  public static Announcement anAnnouncement() {
    Announcement a =
        Announcement.builder()
            .tag(NoticeTag.NOTICE)
            .pinned(false)
            .title("서비스 점검 안내")
            .body("서버 정기 점검으로 해당 시간 동안 서비스가 제한될 수 있어요.")
            .publishedAt(LocalDate.of(2026, 6, 9))
            .build();
    ReflectionTestUtils.setField(a, "id", 1L);
    return a;
  }

  /** 핀된 공지사항 엔티티 (UPDATE, 핀, 2026-06-09). */
  public static Announcement aPinnedAnnouncement() {
    Announcement a =
        Announcement.builder()
            .tag(NoticeTag.UPDATE)
            .pinned(true)
            .title("5월 정기 업데이트")
            .body("결제 페이지에서 쿠폰과 포인트를 한 번에 사용할 수 있도록 개선했어요.")
            .publishedAt(LocalDate.of(2026, 5, 26))
            .build();
    ReflectionTestUtils.setField(a, "id", 2L);
    return a;
  }

  /** 이벤트 공지사항 엔티티 (EVENT, 비핀). */
  public static Announcement anEventAnnouncement() {
    Announcement a =
        Announcement.builder()
            .tag(NoticeTag.EVENT)
            .pinned(false)
            .title("신규 가입 30% 쿠폰 이벤트")
            .body("회원가입 후 마이 → 이벤트에서 쿠폰을 받을 수 있어요.")
            .publishedAt(LocalDate.of(2026, 5, 22))
            .build();
    ReflectionTestUtils.setField(a, "id", 3L);
    return a;
  }

  /** 공지사항 생성 요청. */
  public static AdminAnnouncementCreateRequest aCreateRequest() {
    return new AdminAnnouncementCreateRequest(
        NoticeTag.NOTICE, false, "서비스 점검 안내", "서버 정기 점검으로 서비스가 제한될 수 있어요.");
  }

  /** 공지사항 응답 DTO. */
  public static AnnouncementResponse aResponse() {
    return new AnnouncementResponse(
        1L,
        NoticeTag.NOTICE,
        false,
        LocalDate.of(2026, 6, 9),
        "서비스 점검 안내",
        "서버 정기 점검으로 서비스가 제한될 수 있어요.");
  }

  /** 핀된 공지사항 응답 DTO. */
  public static AnnouncementResponse aPinnedResponse() {
    return new AnnouncementResponse(
        2L,
        NoticeTag.UPDATE,
        true,
        LocalDate.of(2026, 5, 26),
        "5월 정기 업데이트",
        "결제 페이지에서 쿠폰과 포인트를 한 번에 사용할 수 있도록 개선했어요.");
  }
}
