package com.magampick.terms.service;

import com.magampick.customer.domain.Customer;
import com.magampick.global.exception.BusinessException;
import com.magampick.seller.domain.Seller;
import com.magampick.terms.domain.CustomerTermsAgreement;
import com.magampick.terms.domain.SellerTermsAgreement;
import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermRole;
import com.magampick.terms.domain.TermType;
import com.magampick.terms.dto.TermResponse;
import com.magampick.terms.exception.TermErrorCode;
import com.magampick.terms.mapper.TermMapper;
import com.magampick.terms.repository.CustomerTermsAgreementRepository;
import com.magampick.terms.repository.SellerTermsAgreementRepository;
import com.magampick.terms.repository.TermRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermService {

  private static final Set<TermType> CUSTOMER_SIGNUP_TYPES =
      Set.of(
          TermType.TERMS_OF_SERVICE,
          TermType.PRIVACY,
          TermType.LOCATION,
          TermType.AGE_14,
          TermType.MARKETING);
  private static final Set<TermType> SELLER_SIGNUP_TYPES =
      Set.of(
          TermType.TERMS_OF_SERVICE,
          TermType.PRIVACY,
          TermType.LOCATION,
          TermType.AGE_19,
          TermType.MARKETING);

  private final TermRepository termRepository;
  private final CustomerTermsAgreementRepository customerTermsAgreementRepository;
  private final SellerTermsAgreementRepository sellerTermsAgreementRepository;
  private final TermMapper termMapper;

  /** 회원가입 화면에 표시할 약관 목록 (필수 + 선택). */
  public List<TermResponse> getTermsForSignup() {
    return termRepository
        .findByTypeInAndRoleOrderBySortOrderAsc(CUSTOMER_SIGNUP_TYPES, TermRole.CUSTOMER)
        .stream()
        .map(termMapper::toResponse)
        .toList();
  }

  /** 사장 회원가입 화면에 표시할 약관 목록 (필수 + 선택). */
  public List<TermResponse> getTermsForSellerSignup() {
    return termRepository
        .findByTypeInAndRoleOrderBySortOrderAsc(SELLER_SIGNUP_TYPES, TermRole.SELLER)
        .stream()
        .map(termMapper::toResponse)
        .toList();
  }

  /**
   * 가입 시 동의한 약관을 검증·기록한다. 필수 약관(현재 seed = 소비자 5종 중 4종)을 모두 포함해야 하고, 보낸 term id 가 모두 실재해야 한다. 동의 기록은
   * {@code customer_terms_agreements} 에 term 별 1행으로 남긴다.
   */
  @Transactional
  public void recordAgreements(Customer customer, List<Long> agreedTermIds) {
    Set<Long> agreedIds = new HashSet<>(agreedTermIds);
    Set<Long> requiredIds =
        termRepository
            .findByRequiredTrueAndTypeInAndRole(CUSTOMER_SIGNUP_TYPES, TermRole.CUSTOMER)
            .stream()
            .map(Term::getId)
            .collect(Collectors.toSet());
    if (!agreedIds.containsAll(requiredIds)) {
      throw new BusinessException(TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    List<Term> agreedTerms = termRepository.findAllById(agreedIds);
    if (agreedTerms.size() != agreedIds.size()) {
      throw new BusinessException(TermErrorCode.INVALID_TERM);
    }

    customerTermsAgreementRepository.saveAll(
        agreedTerms.stream()
            .map(term -> CustomerTermsAgreement.builder().customer(customer).term(term).build())
            .toList());
  }

  @Transactional
  public void recordSellerAgreements(Seller seller, List<Long> agreedTermIds) {
    Set<Long> agreedIds = new HashSet<>(agreedTermIds);
    Set<Long> requiredIds =
        termRepository
            .findByRequiredTrueAndTypeInAndRole(SELLER_SIGNUP_TYPES, TermRole.SELLER)
            .stream()
            .map(Term::getId)
            .collect(Collectors.toSet());
    if (!agreedIds.containsAll(requiredIds)) {
      throw new BusinessException(TermErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    List<Term> agreedTerms = termRepository.findAllById(agreedIds);
    if (agreedTerms.size() != agreedIds.size()) {
      throw new BusinessException(TermErrorCode.INVALID_TERM);
    }

    sellerTermsAgreementRepository.saveAll(
        agreedTerms.stream()
            .map(term -> SellerTermsAgreement.builder().seller(seller).term(term).build())
            .toList());
  }
}
