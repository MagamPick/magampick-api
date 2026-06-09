package com.magampick.support.repository;

import com.magampick.global.security.Role;
import com.magampick.support.domain.Faq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** FAQ Repository. */
public interface FaqRepository extends JpaRepository<Faq, Long> {

  /** audience 필터 + sort_order 오름차순. */
  List<Faq> findByAudienceOrderBySortOrderAsc(Role audience);
}
