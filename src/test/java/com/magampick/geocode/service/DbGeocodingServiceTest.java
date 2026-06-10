package com.magampick.geocode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.magampick.geocode.domain.GeocodeBuilding;
import com.magampick.geocode.exception.GeocodeErrorCode;
import com.magampick.geocode.repository.GeocodeBuildingRepository;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbGeocodingServiceTest {

  @Mock GeocodeBuildingRepository geocodeBuildingRepository;
  @InjectMocks DbGeocodingService geocodingService;

  @Test
  void 정방향_자연키_매칭_성공() {
    GeocodeBuilding building =
        GeocodeBuilding.builder()
            .roadNameCode("111103100012")
            .underground(false)
            .buildingMainNo(94)
            .buildingSubNo(0)
            .roadAddress("서울특별시 종로구 자하문로 94")
            .location(GeometryUtil.toPoint(37.585, 126.968))
            .build();
    given(
            geocodeBuildingRepository
                .findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
                    "111103100012", false, 94, 0))
        .willReturn(Optional.of(building));

    Point point =
        geocodingService.geocode(new GeocodeQuery("11110", "3100012", "서울특별시 종로구 자하문로 94"));

    assertThat(GeometryUtil.latitude(point)).isEqualTo(37.585);
    assertThat(GeometryUtil.longitude(point)).isEqualTo(126.968);
  }

  @Test
  void 정방향_매칭_미스는_GEOCODING_FAILED() {
    given(
            geocodeBuildingRepository
                .findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
                    "111103100012", false, 94, 0))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                geocodingService.geocode(new GeocodeQuery("11110", "3100012", "서울특별시 종로구 자하문로 94")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", GeocodeErrorCode.GEOCODING_FAILED);
  }

  @Test
  void 정방향_파싱_실패는_조회_없이_GEOCODING_FAILED() {
    assertThatThrownBy(
            () ->
                geocodingService.geocode(
                    new GeocodeQuery("11110", "3100012", "서울특별시 종로구 자하문로 가나다")))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", GeocodeErrorCode.GEOCODING_FAILED);

    then(geocodeBuildingRepository)
        .should(never())
        .findByRoadNameCodeAndUndergroundAndBuildingMainNoAndBuildingSubNo(
            anyString(), anyBoolean(), anyInt(), anyInt());
  }

  @Test
  void 역방향_최근접_라벨_반환() {
    given(geocodeBuildingRepository.findNearestRoadAddress(126.968, 37.585))
        .willReturn(Optional.of("서울특별시 종로구 자하문로 94"));

    String label = geocodingService.reverseGeocode(GeometryUtil.toPoint(37.585, 126.968));

    assertThat(label).isEqualTo("서울특별시 종로구 자하문로 94");
  }

  @Test
  void 역방향_매칭_없으면_null() {
    given(geocodeBuildingRepository.findNearestRoadAddress(anyDouble(), anyDouble()))
        .willReturn(Optional.empty());

    assertThat(geocodingService.reverseGeocode(GeometryUtil.toPoint(37.5, 127.0))).isNull();
  }
}
