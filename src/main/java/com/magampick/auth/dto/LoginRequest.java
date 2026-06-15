package com.magampick.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그인 요청")
public record LoginRequest(
    @Schema(description = "이메일", example = "user@magampick.com") @NotBlank @Email @Size(max = 255)
        String email,
    @Schema(description = "비밀번호", example = "Abcd1234!") @NotBlank String password,
    @Schema(
            description = "로그인 상태 유지 (기본 ON — refresh 쿠키 max-age 30일, OFF 는 세션 쿠키)",
            example = "true")
        Boolean keepSignedIn) {

  /** 미지정(null)은 기본 ON 으로 본다. */
  public boolean persistent() {
    return keepSignedIn == null || keepSignedIn;
  }
}
