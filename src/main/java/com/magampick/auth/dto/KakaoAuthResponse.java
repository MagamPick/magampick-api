package com.magampick.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카카오 로그인 응답. 기존 회원은 {@code status=EXISTING} + access(바디) + refresh(쿠키), 신규 회원은 {@code status=NEW}
 * + 소셜 토큰 + 카카오 prefill. null 필드는 응답에서 생략된다.
 */
@Schema(description = "카카오 로그인 응답 (EXISTING=즉시 로그인 / NEW=추가정보 가입 필요)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KakaoAuthResponse(
    @Schema(description = "EXISTING(기존 회원) | NEW(신규 회원)", example = "EXISTING") String status,
    @Schema(description = "[EXISTING] Access Token") String accessToken,
    @Schema(description = "[EXISTING] Access Token 만료까지 남은 초", example = "1800")
        Long accessExpiresIn,
    @Schema(description = "[NEW] 추가정보 가입용 소셜 토큰") String socialToken,
    @Schema(description = "[NEW] 카카오 이메일") String email,
    @Schema(description = "[NEW] 카카오 닉네임 (prefill)") String nickname) {

  public static KakaoAuthResponse existing(String accessToken, long accessExpiresIn) {
    return new KakaoAuthResponse("EXISTING", accessToken, accessExpiresIn, null, null, null);
  }

  public static KakaoAuthResponse signupRequired(
      String socialToken, String email, String nickname) {
    return new KakaoAuthResponse("NEW", null, null, socialToken, email, nickname);
  }
}
