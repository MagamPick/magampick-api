package com.magampick.terms.repository;

import com.magampick.terms.domain.CustomerTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerTermsAgreementRepository
    extends JpaRepository<CustomerTermsAgreement, Long> {

  /** 가입 오케스트레이션(Step 3)에서 중복 동의 방지에 사용. */
  boolean existsByCustomerIdAndTermId(Long customerId, Long termId);
}
