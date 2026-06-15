package com.magampick.support.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.global.config.JpaAuditingConfig;
import com.magampick.global.security.Role;
import com.magampick.support.domain.Faq;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** FAQ Repository — audience 필터 + sortOrder 정렬 검증. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class FaqRepositoryTest {

  @Autowired FaqRepository faqRepository;

  private Faq buildFaq(Role audience, String question, int sortOrder) {
    return Faq.builder()
        .audience(audience)
        .question(question)
        .answer("답변 내용입니다.")
        .sortOrder(sortOrder)
        .build();
  }

  @Test
  void audience_CUSTOMER_필터_sortOrder_오름차순() {
    // given: seed 에 소비자 FAQ 6건 있음 — 추가로 2건 저장
    Faq c1 = faqRepository.save(buildFaq(Role.CUSTOMER, "추가 질문1", 10));
    Faq c2 = faqRepository.save(buildFaq(Role.CUSTOMER, "추가 질문2", 9));
    // SELLER 1건 (결과에서 제외돼야 함)
    faqRepository.save(buildFaq(Role.SELLER, "사장 질문", 0));

    // when
    List<Faq> result = faqRepository.findByAudienceOrderBySortOrderAsc(Role.CUSTOMER);

    // then: CUSTOMER 것만 (seed 6 + 추가 2 = 8건), sortOrder 오름차순
    List<Faq> addedTwo =
        result.stream()
            .filter(f -> f.getId().equals(c1.getId()) || f.getId().equals(c2.getId()))
            .toList();

    assertThat(addedTwo).hasSize(2);
    // sortOrder 9(c2) < 10(c1) 이므로 c2가 먼저
    assertThat(addedTwo.get(0).getId()).isEqualTo(c2.getId());
    assertThat(addedTwo.get(1).getId()).isEqualTo(c1.getId());

    // SELLER는 결과에 포함되지 않음
    assertThat(result).allMatch(f -> f.getAudience() == Role.CUSTOMER);
  }

  @Test
  void audience_SELLER_필터_sortOrder_오름차순() {
    // given: seed 에 사장 FAQ 6건 있음 — 추가로 1건
    Faq s1 = faqRepository.save(buildFaq(Role.SELLER, "추가 사장 질문", 20));

    // when
    List<Faq> result = faqRepository.findByAudienceOrderBySortOrderAsc(Role.SELLER);

    // then: SELLER 것만
    assertThat(result).allMatch(f -> f.getAudience() == Role.SELLER);
    // 내가 추가한 것도 포함
    assertThat(result.stream().map(Faq::getId).toList()).contains(s1.getId());
  }

  @Test
  void sortOrder_오름차순_정렬_검증() {
    // given: sortOrder 역순 저장
    Faq f3 = faqRepository.save(buildFaq(Role.CUSTOMER, "정렬테스트3", 300));
    Faq f1 = faqRepository.save(buildFaq(Role.CUSTOMER, "정렬테스트1", 100));
    Faq f2 = faqRepository.save(buildFaq(Role.CUSTOMER, "정렬테스트2", 200));

    // when
    List<Faq> result = faqRepository.findByAudienceOrderBySortOrderAsc(Role.CUSTOMER);

    // then: 내가 저장한 것들의 상대 순서가 100, 200, 300
    List<Faq> myThree =
        result.stream()
            .filter(
                f ->
                    f.getId().equals(f1.getId())
                        || f.getId().equals(f2.getId())
                        || f.getId().equals(f3.getId()))
            .toList();

    assertThat(myThree).hasSize(3);
    assertThat(myThree.get(0).getSortOrder()).isEqualTo(100);
    assertThat(myThree.get(1).getSortOrder()).isEqualTo(200);
    assertThat(myThree.get(2).getSortOrder()).isEqualTo(300);
  }
}
