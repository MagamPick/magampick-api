package com.magampick.admin;

import com.magampick.admin.domain.Admin;
import com.magampick.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 앱 시작 시 admin 이 한 명도 없으면 환경변수(ADMIN_EMAIL / ADMIN_PASSWORD)로 기본 admin 을 자동 생성한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeedInitializer implements ApplicationRunner {

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${admin.seed.username:}")
  private String username;

  @Value("${admin.seed.password:}")
  private String password;

  @Value("${admin.seed.name:관리자}")
  private String name;

  @Override
  public void run(ApplicationArguments args) {
    if (username.isBlank() || password.isBlank()) {
      log.info("ADMIN_USERNAME / ADMIN_PASSWORD 미설정 — admin 시드 건너뜀");
      return;
    }

    if (adminRepository.count() > 0) {
      log.info("admin 계정이 이미 존재함 — 시드 건너뜀");
      return;
    }

    Admin admin =
        Admin.builder()
            .username(username)
            .passwordHash(passwordEncoder.encode(password))
            .name(name)
            .build();

    adminRepository.save(admin);
    log.info("기본 admin 계정 생성 완료. username={}", username);
  }
}
