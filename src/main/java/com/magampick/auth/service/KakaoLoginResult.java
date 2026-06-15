package com.magampick.auth.service;

import com.magampick.auth.dto.IssuedTokens;

/**
 * 카카오 1단계(/kakao) 분기 결과. 기존 매핑 회원은 즉시 토큰 발급({@link Existing}), 신규 회원은 가입을 보류하고 소셜 토큰을 발급한다({@link
 * New}).
 */
public sealed interface KakaoLoginResult {

  /** 기존 카카오 매핑 회원 — 즉시 로그인. */
  record Existing(IssuedTokens tokens) implements KakaoLoginResult {}

  /** 신규 회원 — 추가정보 가입(/signup/social) 필요. 소셜 토큰 + 카카오 prefill. */
  record New(String socialToken, String email, String nickname) implements KakaoLoginResult {}
}
