package com.magampick.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "일반 상품 수정 요청 (null 필드는 변경하지 않음)")
public record ProductUpdateRequest(
    @Schema(description = "상품명", example = "크로아상") @Size(max = 50) String name,
    @Schema(description = "정상가 (원, 정수)", example = "4500")
        @DecimalMin(value = "1")
        @Digits(integer = 12, fraction = 0)
        BigDecimal regularPrice) {}
