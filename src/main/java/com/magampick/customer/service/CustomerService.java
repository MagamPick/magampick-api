package com.magampick.customer.service;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.dto.CustomerPhoneUpdateRequest;
import com.magampick.customer.dto.CustomerProfileResponse;
import com.magampick.customer.dto.CustomerProfileUpdateRequest;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.mapper.CustomerMapper;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.phone.service.PhoneVerificationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

  private static final int NICKNAME_MIN = 2;
  private static final int NICKNAME_MAX = 12;

  private final CustomerRepository customerRepository;
  private final CustomerMapper customerMapper;
  private final PhoneVerificationService phoneVerificationService;

  public CustomerProfileResponse getProfile(Long customerId) {
    Customer customer = findActiveCustomer(customerId);
    return customerMapper.toProfileResponse(customer);
  }

  @Transactional
  public CustomerProfileResponse updateProfile(
      Long customerId, CustomerProfileUpdateRequest request) {
    // 소비자 조회
    Customer customer = findActiveCustomer(customerId);
    // 닉네임 검증
    validateNickname(request.nickname());
    // 닉네임 변경
    customer.changeNickname(request.nickname());
    log.info("소비자 닉네임 변경됨. customerId={}", customerId);
    return customerMapper.toProfileResponse(customer);
  }

  @Transactional
  public CustomerProfileResponse updatePhone(Long customerId, CustomerPhoneUpdateRequest request) {
    // 소비자 조회
    Customer customer = findActiveCustomer(customerId);
    // 본인인증 토큰 소비
    String verifiedPhone =
        phoneVerificationService.consumeVerificationToken(
            request.verificationToken(), request.phone());
    // 휴대폰 변경
    customer.changePhone(verifiedPhone, LocalDateTime.now());
    log.info("소비자 휴대폰 변경됨. customerId={}", customerId);
    return customerMapper.toProfileResponse(customer);
  }

  private Customer findActiveCustomer(Long customerId) {
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    if (customer.isDeleted()) {
      throw new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND);
    }
    return customer;
  }

  private void validateNickname(String nickname) {
    int length = nickname == null ? 0 : nickname.trim().length();
    if (length < NICKNAME_MIN || length > NICKNAME_MAX) {
      throw new BusinessException(CustomerErrorCode.NICKNAME_LENGTH);
    }
  }
}
