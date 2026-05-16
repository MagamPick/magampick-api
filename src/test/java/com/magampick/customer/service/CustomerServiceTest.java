package com.magampick.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.dto.CustomerPhoneUpdateRequest;
import com.magampick.customer.dto.CustomerProfileResponse;
import com.magampick.customer.dto.CustomerProfileUpdateRequest;
import com.magampick.customer.exception.CustomerErrorCode;
import com.magampick.customer.mapper.CustomerMapper;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock CustomerRepository customerRepository;
  @Mock CustomerMapper customerMapper;
  @InjectMocks CustomerService customerService;

  private Customer activeCustomer() {
    Customer customer =
        Customer.builder()
            .email("customer@test.com")
            .passwordHash("hash")
            .nickname("마감픽유저")
            .build();
    ReflectionTestUtils.setField(customer, "id", 1L);
    return customer;
  }

  private CustomerProfileResponse stubResponse(Customer customer) {
    return new CustomerProfileResponse(
        customer.getId(),
        customer.getEmail(),
        customer.getNickname(),
        customer.getPhone(),
        null,
        OffsetDateTime.now());
  }

  @Test
  void 프로필_조회_성공() {
    // given
    Customer customer = activeCustomer();
    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
    given(customerMapper.toProfileResponse(customer)).willReturn(stubResponse(customer));

    // when
    CustomerProfileResponse response = customerService.getProfile(1L);

    // then
    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.nickname()).isEqualTo("마감픽유저");
    verify(customerMapper).toProfileResponse(customer);
  }

  @Test
  void 프로필_조회_실패_customerId_미존재() {
    // given
    given(customerRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> customerService.getProfile(999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 프로필_조회_실패_삭제된_customer() {
    // given
    Customer customer = activeCustomer();
    ReflectionTestUtils.setField(customer, "deletedAt", java.time.LocalDateTime.now());
    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

    // when / then
    assertThatThrownBy(() -> customerService.getProfile(1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 닉네임_수정_성공_갱신된_프로필_반환() {
    // given
    Customer customer = activeCustomer();
    CustomerProfileUpdateRequest request = new CustomerProfileUpdateRequest("새닉네임");
    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
    given(customerMapper.toProfileResponse(any(Customer.class)))
        .willAnswer(inv -> stubResponse(inv.getArgument(0)));

    // when
    CustomerProfileResponse response = customerService.updateProfile(1L, request);

    // then
    assertThat(customer.getNickname()).isEqualTo("새닉네임");
    assertThat(response.nickname()).isEqualTo("새닉네임");
    verify(customerMapper).toProfileResponse(customer);
  }

  @Test
  void 닉네임_수정_실패_customerId_미존재() {
    // given
    given(customerRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> customerService.updateProfile(999L, new CustomerProfileUpdateRequest("새닉네임")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 닉네임_수정_실패_삭제된_customer() {
    // given
    Customer customer = activeCustomer();
    ReflectionTestUtils.setField(customer, "deletedAt", java.time.LocalDateTime.now());
    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

    // when / then
    assertThatThrownBy(
            () -> customerService.updateProfile(1L, new CustomerProfileUpdateRequest("새닉네임")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 휴대폰_변경_성공_phoneVerifiedAt_도_갱신됨() {
    // given
    Customer customer = activeCustomer();
    CustomerPhoneUpdateRequest request = new CustomerPhoneUpdateRequest("01099998888");
    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));
    given(customerMapper.toProfileResponse(any(Customer.class)))
        .willAnswer(inv -> stubResponse(inv.getArgument(0)));

    // when
    customerService.updatePhone(1L, request);

    // then
    assertThat(customer.getPhone()).isEqualTo("01099998888");
    assertThat(customer.getPhoneVerifiedAt()).isNotNull();
    verify(customerMapper).toProfileResponse(customer);
  }

  @Test
  void 휴대폰_변경_실패_customerId_미존재() {
    // given
    given(customerRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () -> customerService.updatePhone(999L, new CustomerPhoneUpdateRequest("01012345678")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 휴대폰_변경_실패_삭제된_customer() {
    // given
    Customer customer = activeCustomer();
    ReflectionTestUtils.setField(customer, "deletedAt", java.time.LocalDateTime.now());
    given(customerRepository.findById(1L)).willReturn(Optional.of(customer));

    // when / then
    assertThatThrownBy(
            () -> customerService.updatePhone(1L, new CustomerPhoneUpdateRequest("01012345678")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
  }
}
