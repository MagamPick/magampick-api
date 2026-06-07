package com.magampick.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

import com.magampick.admin.domain.Admin;
import com.magampick.admin.repository.AdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminSeedInitializerTest {

  @Mock AdminRepository adminRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock ApplicationArguments args;

  @InjectMocks AdminSeedInitializer sut;

  @Test
  void admin이_없고_설정이_있으면_admin_생성() throws Exception {
    // given
    ReflectionTestUtils.setField(sut, "email", "admin@magampick.com");
    ReflectionTestUtils.setField(sut, "password", "Admin1234!");
    ReflectionTestUtils.setField(sut, "name", "관리자");
    given(adminRepository.count()).willReturn(0L);
    given(passwordEncoder.encode("Admin1234!")).willReturn("$2a$hash");

    // when
    sut.run(args);

    // then
    verify(adminRepository).save(any(Admin.class));
  }

  @Test
  void admin이_이미_있으면_스킵() throws Exception {
    // given
    ReflectionTestUtils.setField(sut, "email", "admin@magampick.com");
    ReflectionTestUtils.setField(sut, "password", "Admin1234!");
    ReflectionTestUtils.setField(sut, "name", "관리자");
    given(adminRepository.count()).willReturn(1L);

    // when
    sut.run(args);

    // then
    verify(adminRepository, never()).save(any());
  }

  @Test
  void email이_미설정이면_스킵() throws Exception {
    // given
    ReflectionTestUtils.setField(sut, "email", "");
    ReflectionTestUtils.setField(sut, "password", "Admin1234!");
    ReflectionTestUtils.setField(sut, "name", "관리자");

    // when
    sut.run(args);

    // then
    verify(adminRepository, never()).count();
    verify(adminRepository, never()).save(any());
  }

  @Test
  void password가_미설정이면_스킵() throws Exception {
    // given
    ReflectionTestUtils.setField(sut, "email", "admin@magampick.com");
    ReflectionTestUtils.setField(sut, "password", "");
    ReflectionTestUtils.setField(sut, "name", "관리자");

    // when
    sut.run(args);

    // then
    verify(adminRepository, never()).count();
    verify(adminRepository, never()).save(any());
  }
}
