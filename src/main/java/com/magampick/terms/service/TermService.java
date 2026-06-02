package com.magampick.terms.service;

import com.magampick.terms.dto.TermResponse;
import com.magampick.terms.mapper.TermMapper;
import com.magampick.terms.repository.TermRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermService {

  private final TermRepository termRepository;
  private final TermMapper termMapper;

  /** 회원가입 화면에 표시할 약관 목록 (필수 + 선택). */
  public List<TermResponse> getTermsForSignup() {
    return termRepository.findAllByOrderByTypeAsc().stream().map(termMapper::toResponse).toList();
  }
}
