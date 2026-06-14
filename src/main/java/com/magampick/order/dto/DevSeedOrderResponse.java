package com.magampick.order.dto;

import java.math.BigDecimal;

public record DevSeedOrderResponse(
    Long orderId,
    String orderNo,
    String pickupCode,
    String status,
    Long customerId,
    Long storeId,
    BigDecimal finalAmount) {}
