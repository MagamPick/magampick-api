package com.magampick.support.mapper;

import com.magampick.support.domain.Faq;
import com.magampick.support.domain.Inquiry;
import com.magampick.support.dto.FaqResponse;
import com.magampick.support.dto.InquiryAnswerResponse;
import com.magampick.support.dto.InquiryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Support 도메인 MapStruct 매퍼. */
@Mapper(componentModel = "spring")
public interface SupportMapper {

  /**
   * Inquiry → InquiryResponse.
   *
   * <p>createdAt(LocalDateTime) → createdAt(LocalDate) 변환. answer 객체는 {@link
   * #toAnswerResponse(Inquiry)} 로 합성.
   */
  @Mapping(target = "createdAt", expression = "java(inquiry.getCreatedAt().toLocalDate())")
  @Mapping(target = "answer", expression = "java(toAnswerResponse(inquiry))")
  InquiryResponse toResponse(Inquiry inquiry);

  /**
   * Inquiry → InquiryAnswerResponse. answerContent 또는 answeredAt 중 하나라도 null 이면 null 반환.
   *
   * @param inquiry 문의 엔티티
   * @return 답변 응답 (답변 없으면 null)
   */
  default InquiryAnswerResponse toAnswerResponse(Inquiry inquiry) {
    if (inquiry.getAnswerContent() == null || inquiry.getAnsweredAt() == null) {
      return null;
    }
    return new InquiryAnswerResponse(
        inquiry.getAnswerContent(), inquiry.getAnsweredAt().toLocalDate());
  }

  /** Faq → FaqResponse. */
  FaqResponse toFaqResponse(Faq faq);
}
