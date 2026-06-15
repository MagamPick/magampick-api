package com.magampick.support.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.Role;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.support.domain.Faq;
import com.magampick.support.domain.Inquiry;
import com.magampick.support.domain.InquiryStatus;
import com.magampick.support.dto.AdminInquiryAnswerRequest;
import com.magampick.support.dto.FaqResponse;
import com.magampick.support.dto.InquiryCreateRequest;
import com.magampick.support.dto.InquiryResponse;
import com.magampick.support.exception.SupportErrorCode;
import com.magampick.support.fixture.SupportFixture;
import com.magampick.support.mapper.SupportMapper;
import com.magampick.support.repository.FaqRepository;
import com.magampick.support.repository.InquiryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

  @Mock InquiryRepository inquiryRepository;
  @Mock FaqRepository faqRepository;
  @Mock SupportMapper supportMapper;
  @Mock NotificationService notificationService;

  // 2026-06-09 KST 고정 Clock
  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-06-09T01:00:00Z"), ZoneId.of("Asia/Seoul"));

  @InjectMocks SupportService supportService;

  private void injectClock() {
    ReflectionTestUtils.setField(supportService, "clock", fixedClock);
  }

  // ── createInquiry ────────────────────────────────────────────────────────────

  @Test
  void 문의_생성_성공_PENDING_authorRole_authorId_설정() {
    // given
    InquiryCreateRequest req = SupportFixture.aCreateRequest();
    Inquiry saved = SupportFixture.anInquiry();
    InquiryResponse expected = SupportFixture.aResponse();

    given(inquiryRepository.save(any(Inquiry.class))).willReturn(saved);
    given(supportMapper.toResponse(saved)).willReturn(expected);

    // when
    InquiryResponse result = supportService.createInquiry(Role.CUSTOMER, 1L, req);

    // then
    assertThat(result).isEqualTo(expected);
    then(inquiryRepository)
        .should()
        .save(
            argThat(
                i ->
                    i.getAuthorRole() == Role.CUSTOMER
                        && i.getAuthorId().equals(1L)
                        && i.getStatus() == InquiryStatus.PENDING
                        && i.getCategory() == req.category()
                        && i.getTitle().equals(req.title())));
  }

  // ── listMyInquiries ──────────────────────────────────────────────────────────

  @Test
  void 내_문의_목록_author_스코프_최신순() {
    // given
    Inquiry inquiry = SupportFixture.anInquiry();
    InquiryResponse response = SupportFixture.aResponse();

    given(inquiryRepository.findByAuthorRoleAndAuthorIdOrderByCreatedAtDesc(Role.CUSTOMER, 1L))
        .willReturn(List.of(inquiry));
    given(supportMapper.toResponse(inquiry)).willReturn(response);

    // when
    List<InquiryResponse> result = supportService.listMyInquiries(Role.CUSTOMER, 1L);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(response);
  }

  @Test
  void 내_문의_목록_빈_결과() {
    // given
    given(inquiryRepository.findByAuthorRoleAndAuthorIdOrderByCreatedAtDesc(Role.SELLER, 99L))
        .willReturn(List.of());

    // when
    List<InquiryResponse> result = supportService.listMyInquiries(Role.SELLER, 99L);

    // then
    assertThat(result).isEmpty();
  }

  // ── getMyInquiry ─────────────────────────────────────────────────────────────

  @Test
  void 내_문의_상세_성공() {
    // given
    Inquiry inquiry = SupportFixture.anInquiry();
    InquiryResponse expected = SupportFixture.aResponse();

    given(inquiryRepository.findByIdAndAuthorRoleAndAuthorId(1L, Role.CUSTOMER, 1L))
        .willReturn(Optional.of(inquiry));
    given(supportMapper.toResponse(inquiry)).willReturn(expected);

    // when
    InquiryResponse result = supportService.getMyInquiry(Role.CUSTOMER, 1L, 1L);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void 내_문의_상세_타인_것이면_404() {
    // given: 타인 ID 로 조회 시 empty
    given(inquiryRepository.findByIdAndAuthorRoleAndAuthorId(1L, Role.CUSTOMER, 99L))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> supportService.getMyInquiry(Role.CUSTOMER, 99L, 1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SupportErrorCode.INQUIRY_NOT_FOUND);
  }

  // ── answerInquiry ─────────────────────────────────────────────────────────────

  @Test
  void 답변_성공_PENDING에서_ANSWERED로_전이_notifyAlways_호출() {
    // given
    injectClock();
    Inquiry inquiry = SupportFixture.anInquiry();
    AdminInquiryAnswerRequest req = SupportFixture.anAnswerRequest();
    InquiryResponse expected = SupportFixture.anAnsweredResponse();

    given(inquiryRepository.findById(1L)).willReturn(Optional.of(inquiry));
    given(supportMapper.toResponse(inquiry)).willReturn(expected);

    // when
    InquiryResponse result = supportService.answerInquiry(1L, req);

    // then
    assertThat(result).isEqualTo(expected);
    assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
    assertThat(inquiry.getAnswerContent()).isEqualTo(req.content());
    // notifyAlways 호출 검증
    then(notificationService)
        .should()
        .notifyAlways(
            eq(Role.CUSTOMER),
            eq(1L),
            eq(NotificationCategory.INQUIRY),
            any(String.class),
            any(String.class),
            any(String.class));
  }

  @Test
  void 이미_답변된_문의_답변_시_409() {
    // given
    Inquiry answered = SupportFixture.anAnsweredInquiry();
    given(inquiryRepository.findById(1L)).willReturn(Optional.of(answered));

    // when / then
    assertThatThrownBy(() -> supportService.answerInquiry(1L, SupportFixture.anAnswerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SupportErrorCode.INQUIRY_ALREADY_ANSWERED);
  }

  @Test
  void 답변_없는_문의_ID_404() {
    // given
    given(inquiryRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> supportService.answerInquiry(999L, SupportFixture.anAnswerRequest()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", SupportErrorCode.INQUIRY_NOT_FOUND);
  }

  // ── listInquiriesForAdmin ────────────────────────────────────────────────────

  @Test
  void 관리자_목록_필터_없음_PENDING_우선() {
    // given
    Inquiry pending = SupportFixture.anInquiry();
    InquiryResponse response = SupportFixture.aResponse();
    Pageable pageable = PageRequest.of(0, 10);
    Page<Inquiry> page = new PageImpl<>(List.of(pending), pageable, 1);

    given(inquiryRepository.findAllByOrderByStatusDescCreatedAtDesc(pageable)).willReturn(page);
    given(supportMapper.toResponse(pending)).willReturn(response);

    // when
    PageResponse<InquiryResponse> result =
        supportService.listInquiriesForAdmin(null, null, pageable);

    // then
    assertThat(result.content()).hasSize(1);
    assertThat(result.totalCount()).isEqualTo(1);
  }

  // ── listFaqs ─────────────────────────────────────────────────────────────────

  @Test
  void FAQ_목록_audience_필터() {
    // given
    Faq faq = SupportFixture.aCustomerFaq();
    FaqResponse faqResponse = SupportFixture.aFaqResponse();

    given(faqRepository.findByAudienceOrderBySortOrderAsc(Role.CUSTOMER)).willReturn(List.of(faq));
    given(supportMapper.toFaqResponse(faq)).willReturn(faqResponse);

    // when
    List<FaqResponse> result = supportService.listFaqs(Role.CUSTOMER);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).question()).isEqualTo("픽업은 어떻게 하나요?");
  }

  /** Mockito argThat 헬퍼 (람다). */
  private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
    return org.mockito.ArgumentMatchers.argThat(matcher);
  }
}
