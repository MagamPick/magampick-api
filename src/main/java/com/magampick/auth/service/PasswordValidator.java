package com.magampick.auth.service;

import com.magampick.auth.exception.AuthErrorCode;
import com.magampick.global.exception.BusinessException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {

  private static final Pattern PASSWORD_PATTERN =
      Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$");

  public void validate(String password) {
    if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
      throw new BusinessException(AuthErrorCode.PASSWORD_POLICY_VIOLATION);
    }
  }
}
