package com.magampick.seller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerProfileUpdateRequest(
    @NotBlank @Size(min = 1, max = 20) @Schema(description = "사장 이름", example = "홍길동")
        String ownerName) {}
