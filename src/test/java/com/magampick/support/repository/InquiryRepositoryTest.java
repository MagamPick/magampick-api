package com.magampick.support.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.global.security.Role;
import com.magampick.support.domain.Inquiry;
import com.magampick.support.domain.InquiryCategory;
import com.magampick.support.domain.InquiryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** 문의 Repository 쿼리 검증. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class InquiryRepositoryTest {

  @Autowired InquiryRepository inquiryRepository;

  private Inquiry buildInquiry(Role role, Long authorId, InquiryCategory category) {
    return Inquiry.builder()
        .authorRole(role)
        .authorId(authorId)
        .category(category)
        .title("테스트 문의 제목")
        .content("테스트 문의 내용입니다. 충분한 길이.")
        .build();
  }

  // ── 본인 문의 목록 ────────────────────────────────────────────────────────────

  @Test
  void 본인_문의_목록_author_스코프_최신순() {
    // given: customer1 문의 2건, customer2 문의 1건
    Inquiry i1 = inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.PAYMENT));
    Inquiry i2 = inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.ORDER));
    inquiryRepository.save(buildInquiry(Role.CUSTOMER, 2L, InquiryCategory.ETC));

    // when
    List<Inquiry> result =
        inquiryRepository.findByAuthorRoleAndAuthorIdOrderByCreatedAtDesc(Role.CUSTOMER, 1L);

    // then: customer1 것만, 2건, 최신순 (id 내림차순)
    List<Long> myIds = result.stream().map(Inquiry::getId).toList();
    assertThat(myIds).containsExactly(i2.getId(), i1.getId());
  }

  @Test
  void 본인_문의_단건_author_스코프_검증() {
    // given
    Inquiry i = inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.PAYMENT));

    // when: 올바른 author
    Optional<Inquiry> found =
        inquiryRepository.findByIdAndAuthorRoleAndAuthorId(i.getId(), Role.CUSTOMER, 1L);
    // when: 다른 author
    Optional<Inquiry> notFound =
        inquiryRepository.findByIdAndAuthorRoleAndAuthorId(i.getId(), Role.CUSTOMER, 99L);

    // then
    assertThat(found).isPresent();
    assertThat(notFound).isEmpty();
  }

  // ── 관리자 목록 PENDING 우선 ──────────────────────────────────────────────────

  @Test
  void 관리자_목록_PENDING_우선_정렬() {
    // given: PENDING 1개, ANSWERED 1개
    Inquiry pending =
        inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.PAYMENT));
    Inquiry toAnswer =
        inquiryRepository.save(buildInquiry(Role.SELLER, 2L, InquiryCategory.SETTLEMENT));
    toAnswer.answer("답변 완료", LocalDateTime.of(2026, 6, 9, 12, 0));
    inquiryRepository.save(toAnswer);

    // when
    Page<Inquiry> page =
        inquiryRepository.findAllByOrderByStatusDescCreatedAtDesc(PageRequest.of(0, 10));

    // then: 내가 저장한 것 중 PENDING이 먼저
    List<Inquiry> mySaved =
        page.getContent().stream()
            .filter(i -> i.getId().equals(pending.getId()) || i.getId().equals(toAnswer.getId()))
            .toList();

    assertThat(mySaved).hasSize(2);
    assertThat(mySaved.get(0).getStatus()).isEqualTo(InquiryStatus.PENDING);
    assertThat(mySaved.get(1).getStatus()).isEqualTo(InquiryStatus.ANSWERED);
  }

  @Test
  void 관리자_목록_status_필터() {
    // given
    inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.PAYMENT));
    Inquiry answered =
        inquiryRepository.save(buildInquiry(Role.SELLER, 2L, InquiryCategory.SETTLEMENT));
    answered.answer("답변", LocalDateTime.of(2026, 6, 9, 12, 0));
    inquiryRepository.save(answered);

    // when: ANSWERED 만
    Page<Inquiry> page =
        inquiryRepository.findByStatusOrderByStatusDescCreatedAtDesc(
            InquiryStatus.ANSWERED, PageRequest.of(0, 10));

    // then: 모두 ANSWERED
    assertThat(page.getContent()).allMatch(i -> i.getStatus() == InquiryStatus.ANSWERED);
  }

  @Test
  void 관리자_목록_category_필터() {
    // given
    inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.PAYMENT));
    inquiryRepository.save(buildInquiry(Role.CUSTOMER, 1L, InquiryCategory.ORDER));

    // when: PAYMENT 만
    Page<Inquiry> page =
        inquiryRepository.findByCategoryOrderByStatusDescCreatedAtDesc(
            InquiryCategory.PAYMENT, PageRequest.of(0, 10));

    // then: 모두 PAYMENT
    assertThat(page.getContent()).allMatch(i -> i.getCategory() == InquiryCategory.PAYMENT);
  }
}
