package com.magampick.announcement.repository;

import com.magampick.announcement.domain.Announcement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 공지사항 Repository. */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

  /** 핀 우선 → 발행일 최신 → id 내림차순 정렬 전체 목록. */
  List<Announcement> findAllByOrderByPinnedDescPublishedAtDescIdDesc();
}
