package com.magampick.terms.domain;

/** 약관 종류. 가입 시 동의 항목. {@code VARCHAR + CHECK} 로 DB 강제 (erd/overview). */
public enum TermType {
  /** 서비스 이용약관 (필수). */
  TERMS_OF_SERVICE,
  /** 개인정보 수집·이용 동의 (필수). */
  PRIVACY,
  /** 위치 기반 서비스 이용약관 (필수). */
  LOCATION,
  /** 만 14세 이상 확인 (필수, 개인정보보호법 제22조의2). */
  AGE_14,
  /** 마케팅 정보 수신 동의 (선택). */
  MARKETING
}
