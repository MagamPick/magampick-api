package com.magampick.support.repository;

import com.magampick.global.security.Role;
import com.magampick.support.domain.Inquiry;
import com.magampick.support.domain.InquiryCategory;
import com.magampick.support.domain.InquiryStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 문의 Repository. */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

  /** 본인 문의 목록 — 최신순. */
  List<Inquiry> findByAuthorRoleAndAuthorIdOrderByCreatedAtDesc(Role authorRole, Long authorId);

  /** 본인 문의 단건 — author 스코프 검증 포함. */
  Optional<Inquiry> findByIdAndAuthorRoleAndAuthorId(Long id, Role authorRole, Long authorId);

  // ── 관리자 목록 — PENDING 우선(status DESC) → createdAt DESC ─────────────────

  /** 필터 없음. */
  Page<Inquiry> findAllByOrderByStatusDescCreatedAtDesc(Pageable pageable);

  /** status 필터. */
  Page<Inquiry> findByStatusOrderByStatusDescCreatedAtDesc(InquiryStatus status, Pageable pageable);

  /** category 필터. */
  Page<Inquiry> findByCategoryOrderByStatusDescCreatedAtDesc(
      InquiryCategory category, Pageable pageable);

  /** status + category 복합 필터. */
  Page<Inquiry> findByStatusAndCategoryOrderByStatusDescCreatedAtDesc(
      InquiryStatus status, InquiryCategory category, Pageable pageable);
}
