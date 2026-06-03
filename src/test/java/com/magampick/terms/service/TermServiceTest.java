package com.magampick.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermType;
import com.magampick.terms.dto.TermResponse;
import com.magampick.terms.mapper.TermMapper;
import com.magampick.terms.repository.TermRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

  @Mock TermRepository termRepository;
  @Mock TermMapper termMapper;

  @InjectMocks TermService termService;

  @Test
  void 약관_목록_조회_성공() {
    // given
    Term tos =
        Term.builder()
            .type(TermType.TERMS_OF_SERVICE)
            .version(1)
            .title("서비스 이용약관")
            .body("본문")
            .required(true)
            .build();
    Term marketing =
        Term.builder()
            .type(TermType.MARKETING)
            .version(1)
            .title("마케팅 정보 수신 동의")
            .body("본문")
            .required(false)
            .build();
    given(termRepository.findAllByOrderByTypeAsc()).willReturn(List.of(tos, marketing));
    given(termMapper.toResponse(tos))
        .willReturn(new TermResponse(1L, TermType.TERMS_OF_SERVICE, 1, "서비스 이용약관", "본문", true));
    given(termMapper.toResponse(marketing))
        .willReturn(new TermResponse(5L, TermType.MARKETING, 1, "마케팅 정보 수신 동의", "본문", false));

    // when
    List<TermResponse> result = termService.getTermsForSignup();

    // then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(TermResponse::type)
        .containsExactly(TermType.TERMS_OF_SERVICE, TermType.MARKETING);
    assertThat(result)
        .filteredOn(TermResponse::required)
        .extracting(TermResponse::type)
        .containsExactly(TermType.TERMS_OF_SERVICE);
  }
}
