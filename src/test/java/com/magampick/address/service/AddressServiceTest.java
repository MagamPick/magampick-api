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
import com.magampick.geocode.exception.GeocodeErrorCode;
import com.magampick.geocode.service.GeocodeQuery;
import com.magampick.geocode.service.GeocodingService;
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
  @Mock GeocodingService geocodingService;
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
        "집", "서울특별시 강남구 테헤란로 427", null, "101호", "06158", "11680", "3179999");
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

  @Test
  void 주소지_등록_성공_서버_지오코딩_및_첫_등록_default() {
    // given
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(0L);
    given(geocodingService.geocode(any(GeocodeQuery.class)))
        .willReturn(GeometryUtil.toPoint(37.5066, 127.0535));
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
    assertThat(response.latitude()).isEqualTo(37.5066);
    then(geocodingService)
        .should()
        .geocode(new GeocodeQuery("11680", "3179999", "서울특별시 강남구 테헤란로 427"));
  }

  @Test
  void 주소지_등록_성공_두번째부터는_default_FALSE() {
    // given
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(1L);
    given(geocodingService.geocode(any())).willReturn(GeometryUtil.toPoint(37.5066, 127.0535));
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
    then(geocodingService).should(never()).geocode(any());
    then(addressRepository).should(never()).save(any());
  }

  @Test
  void 주소지_등록_실패_별칭_20자_초과() {
    // given
    AddressCreateRequest request =
        new AddressCreateRequest(
            "가".repeat(21), "서울특별시 강남구 테헤란로 427", null, null, "06158", "11680", "3179999");
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(0L);

    // when / then
    assertThatThrownBy(() -> addressService.create(CUSTOMER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.ALIAS_LENGTH);
    then(geocodingService).should(never()).geocode(any());
  }

  @Test
  void 주소지_등록_실패_지오코딩_불가() {
    // given
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(0L);
    given(geocodingService.geocode(any()))
        .willThrow(new BusinessException(GeocodeErrorCode.GEOCODING_FAILED));

    // when / then
    assertThatThrownBy(() -> addressService.create(CUSTOMER_ID, createReq()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.GEOCODING_FAILED);
    then(addressRepository).should(never()).save(any());
  }

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
  void 주소지_수정_성공_label_만_변경하면_지오코딩_없음() {
    // given
    Address existing = address(1L, CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(existing));
    given(addressMapper.toResponse(existing)).willReturn(stubResponse(existing));
    AddressUpdateRequest req = new AddressUpdateRequest("새라벨", null, null, null, null, null, null);

    // when
    addressService.update(CUSTOMER_ID, 1L, req);

    // then
    assertThat(existing.getLabel()).isEqualTo("새라벨");
    assertThat(existing.getRoadAddress()).isEqualTo("서울특별시 강남구 테헤란로 427");
    then(geocodingService).should(never()).geocode(any());
  }

  @Test
  void 주소지_수정_성공_주소_변경시_서버_지오코딩() {
    // given
    Address existing = address(1L, CUSTOMER_ID, false);
    given(addressRepository.findById(1L)).willReturn(Optional.of(existing));
    given(geocodingService.geocode(new GeocodeQuery("11110", "3005001", "서울특별시 종로구 세종대로 175")))
        .willReturn(GeometryUtil.toPoint(37.572, 126.9769));
    given(addressMapper.toResponse(existing)).willReturn(stubResponse(existing));
    AddressUpdateRequest req =
        new AddressUpdateRequest(
            null, "서울특별시 종로구 세종대로 175", "서울특별시 종로구 세종로 1", "2층", "03172", "11110", "3005001");

    // when
    addressService.update(CUSTOMER_ID, 1L, req);

    // then
    assertThat(existing.getRoadAddress()).isEqualTo("서울특별시 종로구 세종대로 175");
    assertThat(GeometryUtil.latitude(existing.getLocation())).isEqualTo(37.572);
    then(geocodingService)
        .should()
        .geocode(new GeocodeQuery("11110", "3005001", "서울특별시 종로구 세종대로 175"));
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
  void 주소지_삭제_성공_기본이_아닌_주소만_삭제() {
    // given
    Address target = address(2L, CUSTOMER_ID, false);
    given(addressRepository.findById(2L)).willReturn(Optional.of(target));
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(2L);

    // when
    addressService.delete(CUSTOMER_ID, 2L);

    // then
    then(addressRepository).should(times(1)).delete(target);
    then(addressRepository).should(never()).flush();
    then(addressRepository)
        .should(never())
        .findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(any(), any());
  }

  @Test
  void 주소지_삭제_실패_마지막_주소() {
    // given
    Address target = address(1L, CUSTOMER_ID, true);
    given(addressRepository.findById(1L)).willReturn(Optional.of(target));
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(1L);

    // when / then
    assertThatThrownBy(() -> addressService.delete(CUSTOMER_ID, 1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.LAST_ADDRESS_DELETE_BLOCKED);
    then(addressRepository).should(never()).delete(any());
  }

  @Test
  void 주소지_삭제_실패_기본_주소() {
    // given
    Address target = address(1L, CUSTOMER_ID, true);
    given(addressRepository.findById(1L)).willReturn(Optional.of(target));
    given(addressRepository.countByCustomerId(CUSTOMER_ID)).willReturn(2L);

    // when / then
    assertThatThrownBy(() -> addressService.delete(CUSTOMER_ID, 1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.DEFAULT_ADDRESS_DELETE_BLOCKED);
    then(addressRepository).should(never()).delete(any());
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
  void requireDefaultLocation_기본주소지_좌표_반환() {
    // given
    Address a = address(1L, CUSTOMER_ID, true);
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.of(a));

    // when
    org.locationtech.jts.geom.Point result = addressService.requireDefaultLocation(CUSTOMER_ID);

    // then
    assertThat(result).isEqualTo(a.getLocation());
  }

  @Test
  void requireDefaultLocation_기본주소지_없으면_DEFAULT_ADDRESS_REQUIRED() {
    // given
    given(addressRepository.findByCustomerIdAndIsDefaultTrue(CUSTOMER_ID))
        .willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> addressService.requireDefaultLocation(CUSTOMER_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.DEFAULT_ADDRESS_REQUIRED);
  }

  @Test
  void 현재위치_역지오코딩_성공() {
    // given
    given(geocodingService.reverseGeocode(GeometryUtil.toPoint(37.5665, 126.9780)))
        .willReturn("서울특별시 중구 세종대로 110");

    // when
    String roadAddress = addressService.reverseGeocode(37.5665, 126.9780);

    // then
    assertThat(roadAddress).isEqualTo("서울특별시 중구 세종대로 110");
  }

  @Test
  void 현재위치_역지오코딩_실패_매칭_없음() {
    // given
    given(geocodingService.reverseGeocode(any())).willReturn(null);

    // when / then
    assertThatThrownBy(() -> addressService.reverseGeocode(37.5665, 126.9780))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", AddressErrorCode.GEOCODING_FAILED);
  }
}
