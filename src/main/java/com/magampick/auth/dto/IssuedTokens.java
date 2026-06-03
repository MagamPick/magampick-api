package com.magampick.auth.dto;

/** 발급된 access + refresh 토큰 (내부 전달용). refresh 는 컨트롤러에서 HttpOnly 쿠키로 내려간다. */
public record IssuedTokens(String accessToken, String refreshToken, long accessExpiresInSeconds) {}
