package com.magampick.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.order.domain.Order;
import com.magampick.order.dto.OrderResponse;
import com.magampick.order.fixture.OrderFixture;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OrderAmountsMapperTest {

  private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

  @Test
  void 혜택_없는_주문_couponDiscount_0_pointUsed_0_finalAmount_payTotal() {
    Order order = OrderFixture.anOrder(OrderFixture.aCustomer(), OrderFixture.aStore());

    OrderResponse.OrderAmountsResponse amounts = mapper.toAmountsResponse(order);

    assertThat(amounts.couponDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(amounts.pointUsed()).isEqualTo(0L);
    assertThat(amounts.finalAmount()).isEqualByComparingTo(amounts.payTotal());
  }

  @Test
  void 쿠폰_포인트_적용_주문_3필드가_DB값대로() {
    Order order = OrderFixture.anOrderWithBenefits(OrderFixture.aCustomer(), OrderFixture.aStore());

    OrderResponse.OrderAmountsResponse amounts = mapper.toAmountsResponse(order);

    assertThat(amounts.couponDiscount()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(amounts.pointUsed()).isEqualTo(500L);
    assertThat(amounts.finalAmount()).isEqualByComparingTo(new BigDecimal("4500"));
  }
}
