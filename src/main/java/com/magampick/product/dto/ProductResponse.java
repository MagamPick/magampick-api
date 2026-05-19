package com.magampick.product.dto;

import com.magampick.product.domain.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "일반 상품 응답")
public record ProductResponse(
    @Schema(description = "상품 ID", example = "1") Long id,
    @Schema(description = "상품명", example = "크로아상") String name,
    @Schema(description = "정상가 (원)", example = "4500") BigDecimal regularPrice,
    @Schema(description = "대표 사진 URL") String imageUrl,
    @Schema(description = "상품 상태") ProductStatus status,
    @Schema(description = "등록 시각") OffsetDateTime createdAt) {}
