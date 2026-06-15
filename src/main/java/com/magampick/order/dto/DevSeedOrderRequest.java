package com.magampick.order.dto;

import com.magampick.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record DevSeedOrderRequest(
    @NotNull OrderStatus targetState, Long customerId, Long storeId, Long clearanceItemId) {}
