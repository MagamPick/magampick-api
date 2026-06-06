package com.magampick.customer.service;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.dto.CustomerPhoneUpdateRequest;
import com.magampick.customer.dto.CustomerProfileResponse;
import com.magampick.customer.dto.CustomerProfileUpdateRequest;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.mapper.CustomerMapper;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
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

  public CustomerProfileResponse getProfile(Long customerId) {
    Customer customer = findActiveCustomer(customerId);
    return customerMapper.toProfileResponse(customer);
  }

  @Transactional
  public CustomerProfileResponse updateProfile(
      Long customerId, CustomerProfileUpdateRequest request) {
    Customer customer = findActiveCustomer(customerId);
    validateNickname(request.nickname());
    customer.changeNickname(request.nickname());
    log.info("소비자 닉네임 변경됨. customerId={}", customerId);
    return customerMapper.toProfileResponse(customer);
  }

  @Transactional
  public CustomerProfileResponse updatePhone(Long customerId, CustomerPhoneUpdateRequest request) {
    Customer customer = findActiveCustomer(customerId);
    customer.changePhone(request.phone(), LocalDateTime.now());
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
