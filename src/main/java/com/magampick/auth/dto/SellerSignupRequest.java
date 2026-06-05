package com.magampick.auth.dto;

import com.magampick.store.dto.StoreCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "사장 회원가입 요청")
public record SellerSignupRequest(
    @Schema(description = "이메일", example = "seller@magampick.com") @NotBlank @Email @Size(max = 255)
        String email,
    @Schema(description = "비밀번호", example = "Abcd1234!")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다")
        String password,
    @Schema(description = "사장 실명", example = "홍길동") @NotBlank @Size(min = 2, max = 20)
        String ownerName,
    @Schema(description = "휴대폰 번호", example = "010-1234-5678") @NotBlank @Size(max = 20)
        String phone,
    @Schema(description = "본인인증 토큰", example = "phone-verification-token") @NotBlank
        String verificationToken,
    @Schema(description = "동의한 약관 ID 목록", example = "[1,2,3,6]") @NotEmpty List<Long> agreedTermIds,
    @Schema(description = "첫 매장 등록 요청") @NotNull @Valid StoreCreateRequest store) {}
