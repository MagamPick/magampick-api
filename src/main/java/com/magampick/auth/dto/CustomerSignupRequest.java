package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "소비자 회원가입 요청")
public record CustomerSignupRequest(
    @Schema(description = "이메일", example = "customer@magampick.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
    @Schema(description = "비밀번호", example = "Abcd1234!")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다")
        String password,
    @Schema(description = "닉네임", example = "마감픽유저") @NotBlank @Size(max = 20) String nickname) {}
