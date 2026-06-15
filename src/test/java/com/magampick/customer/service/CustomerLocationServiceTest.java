package com.magampick.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.customer.domain.CustomerLocation;
import com.magampick.customer.dto.CustomerLocationResponse;
import com.magampick.customer.repository.CustomerLocationRepository;
import com.magampick.global.common.GeometryUtil;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerLocationServiceTest {

  @Mock CustomerLocationRepository customerLocationRepository;
  @InjectMocks CustomerLocationService customerLocationService;

  private static final Long CUSTOMER_ID = 1L;
  private static final double LAT = 37.5665;
  private static final double LNG = 126.9780;

  @Test
  void 신규_위치_저장() {
    // given
    given(customerLocationRepository.findByCustomerId(CUSTOMER_ID)).willReturn(Optional.empty());

    // when
    CustomerLocationResponse response =
        customerLocationService.updateLocation(CUSTOMER_ID, LAT, LNG);

    // then — save 호출
    then(customerLocationRepository).should().save(any(CustomerLocation.class));
    assertThat(response.latitude()).isEqualTo(LAT);
    assertThat(response.longitude()).isEqualTo(LNG);
    assertThat(response.locationUpdatedAt()).isNotNull();
  }

  @Test
  void 기존_위치_덮어쓰기() {
    // given — 이미 위치 정보 존재
    CustomerLocation existing =
        CustomerLocation.of(CUSTOMER_ID, GeometryUtil.toPoint(37.0, 127.0), LocalDateTime.now());
    given(customerLocationRepository.findByCustomerId(CUSTOMER_ID))
        .willReturn(Optional.of(existing));

    // when
    CustomerLocationResponse response =
        customerLocationService.updateLocation(CUSTOMER_ID, LAT, LNG);

    // then — save 미호출, update 만
    then(customerLocationRepository).should(never()).save(any());
    assertThat(response.latitude()).isEqualTo(LAT);
    assertThat(response.longitude()).isEqualTo(LNG);
    assertThat(response.locationUpdatedAt()).isNotNull();
  }

  @Test
  void 응답_위경도_타임스탬프_필드_검증() {
    // given
    given(customerLocationRepository.findByCustomerId(CUSTOMER_ID)).willReturn(Optional.empty());

    // when
    CustomerLocationResponse response =
        customerLocationService.updateLocation(CUSTOMER_ID, LAT, LNG);

    // then
    assertThat(response.latitude()).isCloseTo(LAT, within(0.0001));
    assertThat(response.longitude()).isCloseTo(LNG, within(0.0001));
    assertThat(response.locationUpdatedAt()).isNotNull();
  }
}
