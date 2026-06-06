package com.magampick.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.magampick.customer.domain.Customer;
import com.magampick.global.exception.BusinessException;
import com.magampick.seller.domain.Seller;
import com.magampick.terms.domain.CustomerTermsAgreement;
import com.magampick.terms.domain.SellerTermsAgreement;
import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermType;
import com.magampick.terms.dto.TermResponse;
import com.magampick.terms.exception.TermErrorCode;
import com.magampick.terms.mapper.TermMapper;
import com.magampick.terms.repository.CustomerTermsAgreementRepository;
import com.magampick.terms.repository.SellerTermsAgreementRepository;
import com.magampick.terms.repository.TermRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

  @Mock TermRepository termRepository;
  @Mock CustomerTermsAgreementRepository customerTermsAgreementRepository;
  @Mock SellerTermsAgreementRepository sellerTermsAgreementRepository;
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
    given(termRepository.findByTypeInOrderByTypeAsc(customerTermTypes()))
        .willReturn(List.of(tos, marketing));
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

  @Test
  void 사장_약관_목록_조회_성공() {
    // given
    Term age19 = term(6L, TermType.AGE_19, true);
    Term tos = term(1L, TermType.TERMS_OF_SERVICE, true);
    Term marketing = term(5L, TermType.MARKETING, false);
    given(termRepository.findByTypeInOrderByTypeAsc(sellerTermTypes()))
        .willReturn(List.of(tos, age19, marketing));
    given(termMapper.toResponse(tos))
        .willReturn(new TermResponse(1L, TermType.TERMS_OF_SERVICE, 1, "서비스 이용약관", "본문", true));
    given(termMapper.toResponse(age19))
        .willReturn(new TermResponse(6L, TermType.AGE_19, 1, "만 19세 이상입니다", "본문", true));
    given(termMapper.toResponse(marketing))
        .willReturn(new TermResponse(5L, TermType.MARKETING, 1, "마케팅 정보 수신 동의", "본문", false));

    // when
    List<TermResponse> result = termService.getTermsForSellerSignup();

    // then
    assertThat(result).extracting(TermResponse::type).contains(TermType.AGE_19);
    assertThat(result).extracting(TermResponse::type).doesNotContain(TermType.AGE_14);
  }

  @Test
  void 약관_동의_기록_성공() {
    // given — 필수 2종(1,2) + 선택 1종(5) 동의
    Customer customer = Customer.builder().email("a@test.com").nickname("nick").build();
    given(termRepository.findByRequiredTrueAndTypeIn(customerTermTypes()))
        .willReturn(List.of(term(1L, true), term(2L, true)));
    given(termRepository.findAllById(anyIterable()))
        .willReturn(List.of(term(1L, true), term(2L, true), term(5L, false)));

    // when
    termService.recordAgreements(customer, List.of(1L, 2L, 5L));

    // then — 동의 3건 저장
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CustomerTermsAgreement>> captor = ArgumentCaptor.forClass(List.class);
    verify(customerTermsAgreementRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(3);
  }

  @Test
  void 사장_약관_동의_기록_성공() {
    // given
    Seller seller =
        Seller.builder().email("s@test.com").passwordHash("hash").ownerName("홍길동").build();
    given(termRepository.findByRequiredTrueAndTypeIn(sellerTermTypes()))
        .willReturn(List.of(term(1L, true), term(2L, true), term(6L, TermType.AGE_19, true)));
    given(termRepository.findAllById(anyIterable()))
        .willReturn(
            List.of(
                term(1L, true), term(2L, true), term(5L, false), term(6L, TermType.AGE_19, true)));

    // when
    termService.recordSellerAgreements(seller, List.of(1L, 2L, 5L, 6L));

    // then
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SellerTermsAgreement>> captor = ArgumentCaptor.forClass(List.class);
    verify(sellerTermsAgreementRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(4);
  }

  @Test
  void 사장_필수약관_미동의_시_REQUIRED_TERMS_NOT_AGREED() {
    // given
    Seller seller =
        Seller.builder().email("s@test.com").passwordHash("hash").ownerName("홍길동").build();
    given(termRepository.findByRequiredTrueAndTypeIn(sellerTermTypes()))
        .willReturn(List.of(term(1L, true), term(6L, TermType.AGE_19, true)));

    // when & then
    assertThatThrownBy(() -> termService.recordSellerAgreements(seller, List.of(1L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    verify(sellerTermsAgreementRepository, never()).saveAll(any());
  }

  @Test
  void 필수약관_미동의_시_REQUIRED_TERMS_NOT_AGREED() {
    // given — 필수 2종인데 1종만 동의
    Customer customer = Customer.builder().email("a@test.com").nickname("nick").build();
    given(termRepository.findByRequiredTrueAndTypeIn(customerTermTypes()))
        .willReturn(List.of(term(1L, true), term(2L, true)));

    // when & then
    assertThatThrownBy(() -> termService.recordAgreements(customer, List.of(1L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    verify(customerTermsAgreementRepository, never()).saveAll(any());
  }

  @Test
  void 존재하지_않는_termId_시_INVALID_TERM() {
    // given — 필수 1종 동의했으나 보낸 id 중 99 는 실재 X
    Customer customer = Customer.builder().email("a@test.com").nickname("nick").build();
    given(termRepository.findByRequiredTrueAndTypeIn(customerTermTypes()))
        .willReturn(List.of(term(1L, true)));
    given(termRepository.findAllById(anyIterable())).willReturn(List.of(term(1L, true)));

    // when & then
    assertThatThrownBy(() -> termService.recordAgreements(customer, List.of(1L, 99L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(TermErrorCode.INVALID_TERM);
    verify(customerTermsAgreementRepository, never()).saveAll(any());
  }

  private Term term(Long id, boolean required) {
    return term(id, TermType.TERMS_OF_SERVICE, required);
  }

  private Term term(Long id, TermType type, boolean required) {
    Term term =
        Term.builder().type(type).version(1).title("약관").body("본문").required(required).build();
    ReflectionTestUtils.setField(term, "id", id);
    return term;
  }

  private Set<TermType> sellerTermTypes() {
    return Set.of(
        TermType.TERMS_OF_SERVICE,
        TermType.PRIVACY,
        TermType.LOCATION,
        TermType.AGE_19,
        TermType.MARKETING);
  }

  private Set<TermType> customerTermTypes() {
    return Set.of(
        TermType.TERMS_OF_SERVICE,
        TermType.PRIVACY,
        TermType.LOCATION,
        TermType.AGE_14,
        TermType.MARKETING);
  }
}
