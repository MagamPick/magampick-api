package com.magampick.geocode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoadAddressParserTest {

  @Test
  void 본번만_있는_주소_파싱() {
    GeocodeKey key = RoadAddressParser.parse("11110", "3100012", "서울특별시 종로구 자하문로 94");

    assertThat(key.roadNameCode()).isEqualTo("111103100012");
    assertThat(key.underground()).isFalse();
    assertThat(key.buildingMainNo()).isEqualTo(94);
    assertThat(key.buildingSubNo()).isEqualTo(0);
  }

  @Test
  void 본번_부번_있는_주소_파싱() {
    GeocodeKey key = RoadAddressParser.parse("11110", "3100012", "서울특별시 종로구 자하문로 94-1");

    assertThat(key.buildingMainNo()).isEqualTo(94);
    assertThat(key.buildingSubNo()).isEqualTo(1);
  }

  @Test
  void 지하_접두가_있으면_underground_true() {
    GeocodeKey key = RoadAddressParser.parse("11140", "4100001", "서울특별시 중구 세종대로 지하 2");

    assertThat(key.underground()).isTrue();
    assertThat(key.buildingMainNo()).isEqualTo(2);
    assertThat(key.buildingSubNo()).isEqualTo(0);
  }

  @Test
  void 도로명에_숫자가_있어도_마지막_토큰만_건물번호() {
    GeocodeKey key = RoadAddressParser.parse("11560", "4400015", "서울특별시 영등포구 국회대로62길 10");

    assertThat(key.buildingMainNo()).isEqualTo(10);
    assertThat(key.buildingSubNo()).isEqualTo(0);
  }

  @Test
  void 도로명번호가_7자리_미만이면_좌측_0_패딩() {
    GeocodeKey key = RoadAddressParser.parse("11110", "100012", "서울특별시 종로구 자하문로 94");

    assertThat(key.roadNameCode()).isEqualTo("111100100012");
  }

  @Test
  void 마지막_토큰이_건물번호가_아니면_예외() {
    assertThatThrownBy(() -> RoadAddressParser.parse("11110", "3100012", "서울특별시 종로구 자하문로 가나다"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 시군구코드나_도로명번호가_비면_예외() {
    assertThatThrownBy(() -> RoadAddressParser.parse("", "3100012", "서울특별시 종로구 자하문로 94"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
