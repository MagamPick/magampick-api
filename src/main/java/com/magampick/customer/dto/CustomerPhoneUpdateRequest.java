package com.magampick.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerPhoneUpdateRequest(
    @NotBlank
        @Pattern(regexp = "^010\\d{8}$")
        @Schema(description = "휴대폰 번호 (010 prefix, 숫자 11자리)", example = "01012345678")
        String phone) {}
