package com.magampick.address.service;

import com.magampick.address.domain.Address;
import com.magampick.address.dto.AddressCreateRequest;
import com.magampick.address.dto.AddressResponse;
import com.magampick.address.dto.AddressUpdateRequest;
import com.magampick.address.exception.AddressErrorCode;
import com.magampick.address.mapper.AddressMapper;
import com.magampick.address.repository.AddressRepository;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.exception.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

  /** 한 customer 가 보유할 수 있는 주소지 최대 개수 (정책). */
  private static final int MAX_ADDRESSES_PER_CUSTOMER = 3;

  private final AddressRepository addressRepository;
  private final CustomerRepository customerRepository;
  private final AddressMapper addressMapper;

  @Transactional
  public AddressResponse create(Long customerId, AddressCreateRequest request) {
    long currentCount = addressRepository.countByCustomerId(customerId);
    if (currentCount >= MAX_ADDRESSES_PER_CUSTOMER) {
      throw new BusinessException(AddressErrorCode.ADDRESS_LIMIT_EXCEEDED);
    }
    boolean isDefault = (currentCount == 0);

    // 가벼운 reference — 실제 SELECT 없이 FK 매핑. JWT 통과 customer_id 신뢰.
    Customer customerRef = customerRepository.getReferenceById(customerId);
    Address address =
        Address.builder()
            .customer(customerRef)
            .label(request.label())
            .roadAddress(request.roadAddress())
            .jibunAddress(request.jibunAddress())
            .detailAddress(request.detailAddress())
            .zonecode(request.zonecode())
            .location(GeometryUtil.toPoint(request.latitude(), request.longitude()))
            .isDefault(isDefault)
            .build();
    Address saved = addressRepository.save(address);
    log.info(
        "주소지 등록됨. addressId={}, customerId={}, isDefault={}", saved.getId(), customerId, isDefault);
    return addressMapper.toResponse(saved);
  }

  public List<AddressResponse> list(Long customerId) {
    return addressRepository
        .findByCustomerIdOrderByIsDefaultDescCreatedAtAscIdAsc(customerId)
        .stream()
        .map(addressMapper::toResponse)
        .toList();
  }

  @Transactional
  public AddressResponse update(Long customerId, Long addressId, AddressUpdateRequest request) {
    Address address = findOwnedAddress(customerId, addressId);
    if (request.label() != null) {
      address.changeLabel(request.label());
    }
    if (request.roadAddress() != null) {
      address.changeRoadAddress(request.roadAddress());
    }
    if (request.jibunAddress() != null) {
      address.changeJibunAddress(request.jibunAddress());
    }
    if (request.detailAddress() != null) {
      address.changeDetailAddress(request.detailAddress());
    }
    if (request.zonecode() != null) {
      address.changeZonecode(request.zonecode());
    }
    if (request.latitude() != null && request.longitude() != null) {
      address.changeLocation(request.latitude(), request.longitude());
    }
    log.info("주소지 수정됨. addressId={}, customerId={}", addressId, customerId);
    return addressMapper.toResponse(address);
  }

  @Transactional
  public AddressResponse markAsDefault(Long customerId, Long addressId) {
    Address target = findOwnedAddress(customerId, addressId);
    if (target.isDefault()) {
      // 이미 default — 멱등 처리 (no-op)
      return addressMapper.toResponse(target);
    }
    // 기존 default unset → flush → 신규 default set 순서.
    // 부분 UNIQUE 인덱스 (customer_id WHERE is_default=TRUE) 위반 회피.
    Optional<Address> previousDefault =
        addressRepository.findByCustomerIdAndIsDefaultTrue(customerId);
    previousDefault.ifPresent(Address::unmarkAsDefault);
    addressRepository.flush();
    target.markAsDefault();
    log.info("기본 주소지 변경됨. addressId={}, customerId={}", addressId, customerId);
    return addressMapper.toResponse(target);
  }

  @Transactional
  public void delete(Long customerId, Long addressId) {
    Address target = findOwnedAddress(customerId, addressId);
    boolean wasDefault = target.isDefault();
    addressRepository.delete(target);
    if (wasDefault) {
      // DELETE 가 먼저 반영되어야 부분 UNIQUE 인덱스 위반 없이 새 default 지정 가능.
      addressRepository.flush();
      addressRepository
          .findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(customerId, addressId)
          .ifPresent(
              successor -> {
                successor.markAsDefault();
                log.info(
                    "기본 주소지 자동 승계됨. successorId={}, customerId={}", successor.getId(), customerId);
              });
    }
    log.info(
        "주소지 삭제됨. addressId={}, customerId={}, wasDefault={}", addressId, customerId, wasDefault);
  }

  private Address findOwnedAddress(Long customerId, Long addressId) {
    Address address =
        addressRepository
            .findById(addressId)
            .orElseThrow(() -> new BusinessException(AddressErrorCode.ADDRESS_NOT_FOUND));
    if (!address.isOwnedBy(customerId)) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    return address;
  }
}
