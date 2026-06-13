package com.magampick.refund.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.order.domain.Order;
import com.magampick.order.domain.OrderStatus;
import com.magampick.order.fixture.OrderFixture;
import com.magampick.refund.domain.Refund;
import com.magampick.refund.dto.RefundResponse;
import com.magampick.refund.fixture.RefundFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class RefundMapperTest {

  private final RefundMapper mapper = Mappers.getMapper(RefundMapper.class);

  @Test
  void 환불액_실결제_finalAmount_기준() {
    // given: totalPrice=6000, finalAmount=4500 (쿠폰+포인트 적용) 완료 주문
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), OrderFixture.aStore());
    ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);
    ReflectionTestUtils.setField(order, "completedAt", LocalDateTime.now().minusDays(1));
    Refund refund = RefundFixture.aRequestedRefund(order);

    // when
    RefundResponse response = mapper.toResponse(refund);

    // then: 현금 환불액 = finalAmount (totalPrice 가 아님 — 복원과 합치면 과환불)
    assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("4500"));
  }

  @Test
  void 환불액_finalAmount_null이면_totalPrice_폴백() {
    // given: 혜택 미적용 구주문 — finalAmount=null, totalPrice=6000
    Order order = RefundFixture.aCompletedOrder();
    ReflectionTestUtils.setField(order, "finalAmount", null);
    Refund refund = RefundFixture.aRequestedRefund(order);

    // when
    RefundResponse response = mapper.toResponse(refund);

    // then: finalAmount 없으면 totalPrice 로 폴백 (토스 환불 실연동 전 안전장치)
    assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("6000"));
  }
}
