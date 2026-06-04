package com.magampick.auth.dto;

import com.magampick.address.dto.AddressCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 카카오 신규 회원 추가정보 가입 요청. 소셜 토큰으로 카카오 정보를 복원하고, 약관·본인인증·주소·닉네임을 받는다 (비밀번호 없음 — 소셜 전용 계정). */
@Schema(description = "카카오 신규 회원 추가정보 가입 요청 (소셜 토큰 + 약관·본인인증·주소·닉네임)")
public record SocialSignupRequest(
    @Schema(description = "1단계 /kakao 응답의 소셜 토큰") @NotBlank String socialToken,
    @Schema(description = "닉네임 (2~12자, 카카오 prefill 수정 가능)", example = "마감픽유저") @NotBlank
        String nickname,
    @Schema(description = "휴대폰 번호", example = "010-1234-5678") @NotBlank String phone,
    @Schema(description = "본인인증 토큰 (본인인증 검증 응답의 verificationToken)", example = "a1b2c3d4-...")
        @NotBlank
        String verificationToken,
    @Schema(description = "동의한 약관 ID 목록 (필수 약관 모두 포함)", example = "[1, 2, 3, 4]") @NotEmpty
        List<Long> agreedTermIds,
    @Schema(description = "기본 주소 (좌표 포함)") @Valid AddressCreateRequest address) {}
