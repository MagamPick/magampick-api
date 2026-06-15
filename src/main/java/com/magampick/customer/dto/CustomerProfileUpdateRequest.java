package com.magampick.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerProfileUpdateRequest(
    @NotBlank @Size(min = 2, max = 12) @Schema(description = "닉네임", example = "마감픽유저")
        String nickname) {}
