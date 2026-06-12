package com.magampick.support.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.Role;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.support.domain.Inquiry;
import com.magampick.support.domain.InquiryCategory;
import com.magampick.support.domain.InquiryStatus;
import com.magampick.support.dto.AdminInquiryAnswerRequest;
import com.magampick.support.dto.FaqResponse;
import com.magampick.support.dto.InquiryCreateRequest;
import com.magampick.support.dto.InquiryResponse;
import com.magampick.support.exception.SupportErrorCode;
import com.magampick.support.mapper.SupportMapper;
import com.magampick.support.repository.FaqRepository;
import com.magampick.support.repository.InquiryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객센터 서비스 — FAQ 조회 / 1:1 문의 생성·조회 / 관리자 답변. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

  private final InquiryRepository inquiryRepository;
  private final FaqRepository faqRepository;
  private final SupportMapper supportMapper;
  private final NotificationService notificationService;
  private final Clock clock;

  /**
   * 문의 생성. authorRole / authorId 를 호출 측(컨트롤러)에서 주입.
   *
   * @param authorRole CUSTOMER 또는 SELLER
   * @param authorId 작성자 ID
   * @param req 생성 요청 DTO
   * @return 생성된 문의 응답
   */
  @Transactional
  public InquiryResponse createInquiry(Role authorRole, Long authorId, InquiryCreateRequest req) {
    Inquiry inquiry =
        Inquiry.builder()
            .authorRole(authorRole)
            .authorId(authorId)
            .category(req.category())
            .title(req.title())
            .content(req.content())
            .build();
    return supportMapper.toResponse(inquiryRepository.save(inquiry));
  }

  /**
   * 내 문의 목록 — author 스코프, 최신순.
   *
   * @param authorRole 작성자 역할
   * @param authorId 작성자 ID
   * @return 문의 목록
   */
  public List<InquiryResponse> listMyInquiries(Role authorRole, Long authorId) {
    return inquiryRepository
        .findByAuthorRoleAndAuthorIdOrderByCreatedAtDesc(authorRole, authorId)
        .stream()
        .map(supportMapper::toResponse)
        .toList();
  }

  /**
   * 내 문의 단건 조회 — author 스코프 검증. 본인 것이 아니거나 없으면 404.
   *
   * @param authorRole 작성자 역할
   * @param authorId 작성자 ID
   * @param inquiryId 문의 ID
   * @return 문의 응답
   * @throws BusinessException INQUIRY_NOT_FOUND
   */
  public InquiryResponse getMyInquiry(Role authorRole, Long authorId, Long inquiryId) {
    Inquiry inquiry =
        inquiryRepository
            .findByIdAndAuthorRoleAndAuthorId(inquiryId, authorRole, authorId)
            .orElseThrow(() -> new BusinessException(SupportErrorCode.INQUIRY_NOT_FOUND));
    return supportMapper.toResponse(inquiry);
  }

  /**
   * 관리자 문의 목록 — status / category optional 필터, PENDING 우선 → createdAt DESC.
   *
   * @param status 상태 필터 (null 이면 전체)
   * @param category 카테고리 필터 (null 이면 전체)
   * @param pageable 페이징
   * @return 페이징된 문의 목록
   */
  public PageResponse<InquiryResponse> listInquiriesForAdmin(
      InquiryStatus status, InquiryCategory category, Pageable pageable) {
    Page<Inquiry> page;
    if (status != null && category != null) {
      page =
          inquiryRepository.findByStatusAndCategoryOrderByStatusDescCreatedAtDesc(
              status, category, pageable);
    } else if (status != null) {
      page = inquiryRepository.findByStatusOrderByStatusDescCreatedAtDesc(status, pageable);
    } else if (category != null) {
      page = inquiryRepository.findByCategoryOrderByStatusDescCreatedAtDesc(category, pageable);
    } else {
      page = inquiryRepository.findAllByOrderByStatusDescCreatedAtDesc(pageable);
    }
    return PageResponse.of(page.map(supportMapper::toResponse));
  }

  /**
   * 관리자 답변 처리. PENDING 만 가능 — ANSWERED 이면 409. 답변 후 always-on 알림 발송.
   *
   * @param inquiryId 문의 ID
   * @param req 답변 요청 DTO
   * @return 답변된 문의 응답
   * @throws BusinessException INQUIRY_NOT_FOUND / INQUIRY_ALREADY_ANSWERED
   */
  @Transactional
  public InquiryResponse answerInquiry(Long inquiryId, AdminInquiryAnswerRequest req) {
    // 문의 조회
    Inquiry inquiry =
        inquiryRepository
            .findById(inquiryId)
            .orElseThrow(() -> new BusinessException(SupportErrorCode.INQUIRY_NOT_FOUND));

    // 중복 답변 검증
    if (inquiry.getStatus() == InquiryStatus.ANSWERED) {
      throw new BusinessException(SupportErrorCode.INQUIRY_ALREADY_ANSWERED);
    }

    // 답변 처리
    inquiry.answer(req.content(), LocalDateTime.now(clock));

    // 답변 알림 — always-on (설정 토글 무관)
    String link = "/support/inquiry/" + inquiry.getId();
    notificationService.notifyAlways(
        inquiry.getAuthorRole(),
        inquiry.getAuthorId(),
        NotificationCategory.INQUIRY,
        "문의 답변이 등록됐어요",
        "'" + inquiry.getTitle() + "' 문의에 답변이 등록되었습니다.",
        link);

    return supportMapper.toResponse(inquiry);
  }

  /**
   * FAQ 목록 — audience 별, sortOrder 오름차순.
   *
   * @param audience 대상 역할 (CUSTOMER 또는 SELLER)
   * @return FAQ 목록
   */
  public List<FaqResponse> listFaqs(Role audience) {
    return faqRepository.findByAudienceOrderBySortOrderAsc(audience).stream()
        .map(supportMapper::toFaqResponse)
        .toList();
  }
}
