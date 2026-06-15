package com.magampick.terms.repository;

import com.magampick.terms.domain.Term;
import com.magampick.terms.domain.TermRole;
import com.magampick.terms.domain.TermType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

  /** 가입 역할별 약관 목록 (표시 순서). */
  List<Term> findByTypeInAndRoleOrderBySortOrderAsc(Collection<TermType> types, TermRole role);

  /** 가입 역할별 필수 약관. */
  List<Term> findByRequiredTrueAndTypeInAndRole(Collection<TermType> types, TermRole role);
}
