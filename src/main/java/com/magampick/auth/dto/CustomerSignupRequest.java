package com.magampick.auth.dto;

import com.magampick.address.dto.AddressCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "소비자 회원가입 요청 (약관·본인인증·주소·닉네임 통합)")
public record CustomerSignupRequest(
    @Schema(description = "이메일", example = "customer@magampick.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
    @Schema(description = "비밀번호 (8자 이상, 영문·숫자·특수문자 포함)", example = "Abcd1234!")
        @NotBlank
        @Size(max = 72)
        String password,
    @Schema(description = "닉네임 (2~12자)", example = "마감픽유저") @NotBlank String nickname,
    @Schema(description = "휴대폰 번호", example = "010-1234-5678") @NotBlank String phone,
    @Schema(description = "본인인증 토큰 (본인인증 검증 응답의 verificationToken)", example = "a1b2c3d4-...")
        @NotBlank
        String verificationToken,
    @Schema(description = "동의한 약관 ID 목록 (필수 약관 모두 포함)", example = "[1, 2, 3, 4]") @NotEmpty
        List<Long> agreedTermIds,
    @Schema(description = "기본 주소 (좌표 포함)") @Valid AddressCreateRequest address) {}
