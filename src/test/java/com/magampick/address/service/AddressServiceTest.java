package com.magampick.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

  private static final Long CUSTOMER_ID = 1L;
  private static final Long OTHER_CUSTOMER_ID = 99L;

  @Mock AddressRepository addressRepository;
  @Mock CustomerRepository customerRepository;
  @Mock AddressMapper addressMapper;
  @InjectMocks AddressService addressService;

  private Customer customerRef(Long id) {
    Customer customer =
        Customer.builder().email("c@test.com").nickname("nick").passwordHash("hash").build();
    ReflectionTestUtils.setField(customer, "id", id);
    return customer;
  }

  private Address address(Long id, Long ownerId, boolean isDefault) {
    Address a =
        Address.builder()
            .customer(customerRef(ownerId))
            .label("집")
            .roadAddress("서울특별시 강남구 테헤란로 427")
            .jibunAddress(null)
            .detailAddress(null)
            .zonecode("06158")
            .location(GeometryUtil.toPoint(37.5066, 127.0535))
            .isDefault(isDefault)
            .build();
    ReflectionTestUtils.setField(a, "id", id);
    return a;
  }

  private AddressCreateRequest createReq() {
    return new AddressCreateRequest(
        "집", "서울특별시 강남구 테헤란로 427", null, "101호", "06158", 37.5066, 127.0535);
  }

  private AddressResponse stubResponse(Address a) {
    return new AddressResponse(
        a.getId(),
        a.getLabel(),
        a.getRoadAddress(),
        a.getJibunAddress(),
        a.getDetailAddress(),
        a.getZonecode(),
        GeometryUtil.latitude(a.getLocation()),
        GeometryUtil.longitude(a.getLocation()),
        a.isDefault(),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  // ── 등록 ──────────────────────────────────────────────────────────────────

  @Test
  void 주소지_등록_성공_첫_등록은_default_자동_지정() {
    // given
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(0L);
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(customerRef(CUSTOMER_ID));
    given(addressRepository.save(any(Address.class)))
        .willAnswer(
            inv -> {
              Address a = inv.getArgument(0);
              ReflectionTestUtils.setField(a, "id", 10L);
              return a;
            });
    given(addressMapper.toResponse(any(Address.class)))
        .willAnswer(inv -> stubResponse(inv.getArgument(0)));

    // when
    AddressResponse response = addressService.create(CUSTOMER_ID, createReq());

    // then
    assertThat(response.id()).isEqualTo(10L);
    assertThat(response.isDefault()).isTrue();
  }

  @Test
  void 주소지_등록_성공_두번째_부터는_default_FALSE() {
    // given
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(1L);
    given(customerRepository.getReferenceById(CUSTOMER_ID)).willReturn(customerRef(CUSTOMER_ID));
    given(addressRepository.save(any(Address.class)))
        .willAnswer(
            inv -> {
              Address a = inv.getArgument(0);
              ReflectionTestUtils.setField(a, "id", 11L);
              return a;
            });
    given(addressMapper.toResponse(any(Address.class)))
        .willAnswer(inv -> stubResponse(inv.getArgument(0)));

    // when
    AddressResponse response = addressService.create(CUSTOMER_ID, createReq());

    // then
    assertThat(response.isDefault()).isFalse();
  }

  @Test
  void 주소지_등록_실패_보유_한도_초과() {
    // given
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(3L);

    // when / then
    assertThatThrownBy(() -> addressService.create(CUSTOMER_ID, createReq()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.ADDRESS_LIMIT_EXCEEDED);
    then(addressRepository).should(never()).save(any());
  }

  // ── 목록 조회 ────────────────────────────────────────────────────────────

  @Test
  void 주소지_목록_조회_성공_default_가_맨_위() {
    // given
    Address defaultAddr = address(1L, CUSTOMER_ID, true);
    Address other = address(2L, CUSTOMER_ID, false);
    given(addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAscIdAsc(CUSTOMER_ID))
        .willReturn(List.of(defaultAddr, other));
    given(addressMapper.toResponse(defaultAddr)).willReturn(stubResponse(defaultAddr));
    given(addressMapper.toResponse(other)).willReturn(stubResponse(other));

    // when
    List<AddressResponse> result = addressService.list(CUSTOMER_ID);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).isDefault()).isTrue();
    assertThat(result.get(0).id()).isEqualTo(1L);
  }

  @Test
  void 주소지_목록_조회_성공_보유_0개면_빈_리스트() {
    // given
    given(addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAscIdAsc(CUSTOMER_ID))
        .willReturn(List.of());

    // when
    List<AddressResponse> result = addressService.list(CUSTOMER_ID);

    // then
    assertThat(result).isEmpty();
  }

  // ── 수정 (PATCH) ─────────────────────────────────────────────────────────

  @Test
  void 주소지_수정_성공_label_만_변경() {
    // given
    Address existing = address(1L, CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(existing));
    given(addressMapper.toResponse(existing)).willReturn(stubResponse(existing));
    AddressUpdateRequest req = new AddressUpdateRequest("새라벨", null, null, null, null, null, null);

    // when
    addressService.update(CUSTOMER_ID, 1L, req);

    // then
    assertThat(existing.getLabel()).isEqualTo("새라벨");
    assertThat(existing.getRoadAddress()).isEqualTo("서울특별시 강남구 테헤란로 427"); // 변경 안 됨
  }

  @Test
  void 주소지_수정_성공_좌표_쌍_변경() {
    // given
    Address existing = address(1L, CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(existing));
    given(addressMapper.toResponse(existing)).willReturn(stubResponse(existing));
    AddressUpdateRequest req =
        new AddressUpdateRequest(null, null, null, null, null, 35.1796, 129.0756);

    // when
    addressService.update(CUSTOMER_ID, 1L, req);

    // then
    assertThat(GeometryUtil.latitude(existing.getLocation())).isEqualTo(35.1796);
    assertThat(GeometryUtil.longitude(existing.getLocation())).isEqualTo(129.0756);
  }

  @Test
  void 주소지_수정_실패_본인_외_주소() {
    // given
    Address other = address(1L, OTHER_CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(other));

    // when / then
    assertThatThrownBy(
            () ->
                addressService.update(
                    CUSTOMER_ID,
                    1L,
                    new AddressUpdateRequest("x", null, null, null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
  }

  @Test
  void 주소지_수정_실패_존재하지_않음() {
    // given
    given(addressRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(
            () ->
                addressService.update(
                    CUSTOMER_ID,
                    999L,
                    new AddressUpdateRequest("x", null, null, null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.ADDRESS_NOT_FOUND);
  }

  // ── 기본 주소지 변경 (POST .../default) ─────────────────────────────────

  @Test
  void 기본_주소지_변경_성공_기존_default_unset() {
    // given
    Address previousDefault = address(1L, CUSTOMER_ID, true);
    Address target = address(2L, CUSTOMER_ID, false);
    given(addressRepository.findById(2L)).willReturn(Optional.of(target));
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(previousDefault));
    given(addressMapper.toResponse(target)).willReturn(stubResponse(target));

    // when
    addressService.markAsDefault(CUSTOMER_ID, 2L);

    // then
    assertThat(previousDefault.isDefault()).isFalse();
    assertThat(target.isDefault()).isTrue();
    then(addressRepository).should(times(1)).flush();
  }

  @Test
  void 기본_주소지_변경_성공_이미_default_면_멱등() {
    // given
    Address target = address(1L, CUSTOMER_ID, true);
    given(addressRepository.findById(1L)).willReturn(Optional.of(target));
    given(addressMapper.toResponse(target)).willReturn(stubResponse(target));

    // when
    addressService.markAsDefault(CUSTOMER_ID, 1L);

    // then
    then(addressRepository).should(never()).findByCustomerIdAndIsDefaultTrue(any());
    then(addressRepository).should(never()).flush();
    assertThat(target.isDefault()).isTrue();
  }

  @Test
  void 기본_주소지_변경_실패_본인_외_주소() {
    // given
    Address other = address(1L, OTHER_CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(other));

    // when / then
    assertThatThrownBy(() -> addressService.markAsDefault(CUSTOMER_ID, 1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
  }

  @Test
  void 기본_주소지_변경_실패_존재하지_않음() {
    // given
    given(addressRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> addressService.markAsDefault(CUSTOMER_ID, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.ADDRESS_NOT_FOUND);
  }

  // ── 삭제 ────────────────────────────────────────────────────────────────

  @Test
  void 주소지_삭제_성공() {
    // given
    Address target = address(1L, CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(target));

    // when
    addressService.delete(CUSTOMER_ID, 1L);

    // then
    then(addressRepository).should(times(1)).delete(target);
    then(addressRepository).should(never()).flush();
    then(addressRepository)
        .should(never())
        .findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(any(), any());
  }

  @Test
  void 주소지_삭제_성공_default_삭제시_가장_오래된_주소_자동_승계() {
    // given
    Address target = address(1L, CUSTOMER_ID, true);
    Address successor = address(2L, CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(target));
    given(addressRepository.findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(CUSTOMER_ID, 1L))
        .willReturn(Optional.of(successor));

    // when
    addressService.delete(CUSTOMER_ID, 1L);

    // then
    then(addressRepository).should(times(1)).delete(target);
    then(addressRepository).should(times(1)).flush();
    assertThat(successor.isDefault()).isTrue();
  }

  @Test
  void 주소지_삭제_성공_마지막_1개_삭제시_default_승계_없이_종료() {
    // given
    Address target = address(1L, CUSTOMER_ID, true);
    given(addressRepository.findById(1L)).willReturn(Optional.of(target));
    given(addressRepository.findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(CUSTOMER_ID, 1L))
        .willReturn(Optional.empty());

    // when
    addressService.delete(CUSTOMER_ID, 1L);

    // then
    then(addressRepository).should(times(1)).delete(target);
    then(addressRepository).should(times(1)).flush();
    // 승계 대상 없음 → markAsDefault 호출 없음 — 외부 effect 없음
  }

  @Test
  void 주소지_삭제_실패_본인_외_주소() {
    // given
    Address other = address(1L, OTHER_CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(other));

    // when / then
    assertThatThrownBy(() -> addressService.delete(CUSTOMER_ID, 1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
  }

  @Test
  void 주소지_삭제_실패_존재하지_않음() {
    // given
    given(addressRepository.findById(999L)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> addressService.delete(CUSTOMER_ID, 999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.ADDRESS_NOT_FOUND);
  }
}
