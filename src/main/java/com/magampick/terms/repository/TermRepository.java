package com.magampick.terms.repository;

import com.magampick.terms.domain.Term;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

  /** 가입 화면 표시용 약관 목록 (type 순). */
  List<Term> findAllByOrderByTypeAsc();
}
