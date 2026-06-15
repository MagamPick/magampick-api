package com.magampick.global.security;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 검증된 JWT claim 에서 만든 인증 주체. stateless 라 DB 조회(UserDetailsService) 없이 토큰 claim 만 신뢰한다. 비밀번호는 사용하지
 * 않으므로 빈 문자열.
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

  private final Long userId;
  private final Role role;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(role.toAuthority());
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return String.valueOf(userId);
  }
}
