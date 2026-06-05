package com.magampick.terms.repository;

import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

  /** 가입 화면 표시용 약관 목록 (type 순). */
  List<Term> findAllByOrderByTypeAsc();

  /** 필수 약관 (가입 시 모두 동의 강제). */
  List<Term> findByRequiredTrue();

  /** 가입 역할별 약관 목록 (type 순). */
  List<Term> findByTypeInOrderByTypeAsc(Collection<TermType> types);

  /** 가입 역할별 필수 약관. */
  List<Term> findByRequiredTrueAndTypeIn(Collection<TermType> types);
}
