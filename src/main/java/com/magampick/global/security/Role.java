package com.magampick.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** 사용자 역할. JWT role claim 및 Spring Security 권한(ROLE_ prefix) 의 원천. */
public enum Role {
  CUSTOMER,
  SELLER,
  ADMIN;

  public GrantedAuthority toAuthority() {
    return new SimpleGrantedAuthority("ROLE_" + name());
  }
}
