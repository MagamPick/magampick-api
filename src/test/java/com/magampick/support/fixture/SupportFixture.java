package com.magampick.support.fixture;

import com.magampick.global.security.Role;
import com.magampick.support.domain.Faq;
import com.magampick.support.domain.Inquiry;
import com.magampick.support.domain.InquiryCategory;
import com.magampick.support.domain.InquiryStatus;
import com.magampick.support.dto.AdminInquiryAnswerRequest;
import com.magampick.support.dto.FaqResponse;
import com.magampick.support.dto.InquiryAnswerResponse;
import com.magampick.support.dto.InquiryCreateRequest;
import com.magampick.support.dto.InquiryResponse;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

/** Support 도메인 테스트 픽스처. */
public class SupportFixture {

  private SupportFixture() {}

  /** 기본 문의 엔티티 (CUSTOMER, PENDING). */
  public static Inquiry anInquiry() {
    Inquiry i =
        Inquiry.builder()
            .authorRole(Role.CUSTOMER)
            .authorId(1L)
            .category(InquiryCategory.PAYMENT)
            .title("결제 오류 문의")
            .content("결제 시 오류가 발생했어요. 확인 부탁드립니다.")
            .build();
    ReflectionTestUtils.setField(i, "id", 1L);
    ReflectionTestUtils.setField(i, "createdAt", java.time.LocalDateTime.of(2026, 6, 9, 10, 0));
    ReflectionTestUtils.setField(i, "updatedAt", java.time.LocalDateTime.of(2026, 6, 9, 10, 0));
    return i;
  }

  /** 답변된 문의 엔티티 (CUSTOMER, ANSWERED). */
  public static Inquiry anAnsweredInquiry() {
    Inquiry i = anInquiry();
    i.answer("확인 후 처리해 드렸습니다.", java.time.LocalDateTime.of(2026, 6, 9, 14, 0));
    return i;
  }

  /** 사장 문의 엔티티 (SELLER, PENDING). */
  public static Inquiry aSellerInquiry() {
    Inquiry i =
        Inquiry.builder()
            .authorRole(Role.SELLER)
            .authorId(2L)
            .category(InquiryCategory.SETTLEMENT)
            .title("정산 관련 문의")
            .content("이번 달 정산 내역이 이상한 것 같습니다.")
            .build();
    ReflectionTestUtils.setField(i, "id", 2L);
    ReflectionTestUtils.setField(i, "createdAt", java.time.LocalDateTime.of(2026, 6, 9, 9, 0));
    ReflectionTestUtils.setField(i, "updatedAt", java.time.LocalDateTime.of(2026, 6, 9, 9, 0));
    return i;
  }

  /** 문의 생성 요청 픽스처. */
  public static InquiryCreateRequest aCreateRequest() {
    return new InquiryCreateRequest(
        InquiryCategory.PAYMENT, "결제 오류 문의", "결제 시 오류가 발생했어요. 확인 부탁드립니다.");
  }

  /** 관리자 답변 요청 픽스처. */
  public static AdminInquiryAnswerRequest anAnswerRequest() {
    return new AdminInquiryAnswerRequest("확인 후 처리해 드렸습니다.");
  }

  /** 문의 응답 DTO (PENDING). */
  public static InquiryResponse aResponse() {
    return new InquiryResponse(
        1L,
        InquiryCategory.PAYMENT,
        "결제 오류 문의",
        "결제 시 오류가 발생했어요. 확인 부탁드립니다.",
        InquiryStatus.PENDING,
        LocalDate.of(2026, 6, 9),
        null);
  }

  /** 문의 응답 DTO (ANSWERED). */
  public static InquiryResponse anAnsweredResponse() {
    return new InquiryResponse(
        1L,
        InquiryCategory.PAYMENT,
        "결제 오류 문의",
        "결제 시 오류가 발생했어요. 확인 부탁드립니다.",
        InquiryStatus.ANSWERED,
        LocalDate.of(2026, 6, 9),
        new InquiryAnswerResponse("확인 후 처리해 드렸습니다.", LocalDate.of(2026, 6, 9)));
  }

  /** 소비자 FAQ 엔티티. */
  public static Faq aCustomerFaq() {
    Faq f =
        Faq.builder()
            .audience(Role.CUSTOMER)
            .question("픽업은 어떻게 하나요?")
            .answer("결제 완료 후 받은 4자리 픽업 코드를 매장 직원에게 보여주시면 바로 받을 수 있어요.")
            .sortOrder(0)
            .build();
    ReflectionTestUtils.setField(f, "id", 1L);
    ReflectionTestUtils.setField(f, "createdAt", java.time.LocalDateTime.of(2026, 6, 9, 0, 0));
    ReflectionTestUtils.setField(f, "updatedAt", java.time.LocalDateTime.of(2026, 6, 9, 0, 0));
    return f;
  }

  /** 소비자 FAQ 응답 DTO. */
  public static FaqResponse aFaqResponse() {
    return new FaqResponse(1L, "픽업은 어떻게 하나요?", "결제 완료 후 받은 4자리 픽업 코드를 매장 직원에게 보여주시면 바로 받을 수 있어요.");
  }
}
