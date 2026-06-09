package com.magampick.announcement.service;

import com.magampick.announcement.domain.Announcement;
import com.magampick.announcement.dto.AdminAnnouncementCreateRequest;
import com.magampick.announcement.dto.AdminAnnouncementUpdateRequest;
import com.magampick.announcement.dto.AnnouncementResponse;
import com.magampick.announcement.exception.AnnouncementErrorCode;
import com.magampick.announcement.mapper.AnnouncementMapper;
import com.magampick.announcement.repository.AnnouncementRepository;
import com.magampick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지사항 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

  private final AnnouncementRepository announcementRepository;
  private final AnnouncementMapper announcementMapper;
  private final Clock clock;

  /**
   * 공지사항 목록 조회. 핀 우선 → 발행일 최신 → id 내림차순 정렬.
   *
   * @return 공지사항 목록
   */
  public List<AnnouncementResponse> list() {
    return announcementRepository.findAllByOrderByPinnedDescPublishedAtDescIdDesc().stream()
        .map(announcementMapper::toResponse)
        .toList();
  }

  /**
   * 공지사항 생성. publishedAt = 생성 당일.
   *
   * @param req 생성 요청
   * @return 생성된 공지사항 응답
   */
  @Transactional
  public AnnouncementResponse create(AdminAnnouncementCreateRequest req) {
    Announcement announcement =
        Announcement.builder()
            .tag(req.tag())
            .pinned(req.pinned())
            .title(req.title())
            .body(req.body())
            .publishedAt(LocalDate.now(clock))
            .build();
    return announcementMapper.toResponse(announcementRepository.save(announcement));
  }

  /**
   * 공지사항 부분 수정. null 필드는 유지한다.
   *
   * @param announcementId 수정할 공지사항 ID
   * @param req 수정 요청 (null 필드 = 미수정)
   * @return 수정된 공지사항 응답
   * @throws BusinessException ANNOUNCEMENT_NOT_FOUND
   */
  @Transactional
  public AnnouncementResponse update(Long announcementId, AdminAnnouncementUpdateRequest req) {
    Announcement announcement =
        announcementRepository
            .findById(announcementId)
            .orElseThrow(() -> new BusinessException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND));
    announcement.update(req.tag(), req.pinned(), req.title(), req.body());
    return announcementMapper.toResponse(announcement);
  }

  /**
   * 공지사항 삭제.
   *
   * @param announcementId 삭제할 공지사항 ID
   * @throws BusinessException ANNOUNCEMENT_NOT_FOUND
   */
  @Transactional
  public void delete(Long announcementId) {
    Announcement announcement =
        announcementRepository
            .findById(announcementId)
            .orElseThrow(() -> new BusinessException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND));
    announcementRepository.delete(announcement);
  }
}
