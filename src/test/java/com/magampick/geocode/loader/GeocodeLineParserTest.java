package com.magampick.geocode.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeocodeLineParserTest {

  // 실제 위치정보요약DB 1행 (청운동 자하문로 94)
  private static final String SAMPLE =
      "11110|760|1111010100|서울특별시|종로구|청운동|111103100012|자하문로|0|94|0||03047|근린생활시설|0|청운효자동|953241.683263|1954023.466812";

  @Test
  void 정상_행_파싱_부번0() {
    GeocodeRow row = GeocodeLineParser.parse(SAMPLE).orElseThrow();

    assertThat(row.roadNameCode()).isEqualTo("111103100012");
    assertThat(row.underground()).isFalse();
    assertThat(row.buildingMainNo()).isEqualTo(94);
    assertThat(row.buildingSubNo()).isEqualTo(0);
    assertThat(row.roadAddress()).isEqualTo("서울특별시 종로구 자하문로 94");
    assertThat(row.x()).isEqualTo(953241.683263);
    assertThat(row.y()).isEqualTo(1954023.466812);
  }

  @Test
  void 부번이_있으면_라벨에_하이픈() {
    String line =
        "11110|760|1111010100|서울특별시|종로구|청운동|111103100012|자하문로|0|94|1||03047|주택|0|청운효자동|953241.683263|1954023.466812";

    GeocodeRow row = GeocodeLineParser.parse(line).orElseThrow();

    assertThat(row.buildingSubNo()).isEqualTo(1);
    assertThat(row.roadAddress()).isEqualTo("서울특별시 종로구 자하문로 94-1");
  }

  @Test
  void 지하여부_1이면_라벨에_지하() {
    String line =
        "11140|1|1114000000|서울특별시|중구|정동|111404100001|세종대로|1|2|0||04520|업무시설|0|소공동|953000.0|1952000.0";

    GeocodeRow row = GeocodeLineParser.parse(line).orElseThrow();

    assertThat(row.underground()).isTrue();
    assertThat(row.roadAddress()).isEqualTo("서울특별시 중구 세종대로 지하 2");
  }

  @Test
  void 좌표_누락_행은_skip() {
    String line =
        "11110|760|1111010100|서울특별시|종로구|청운동|111103100012|자하문로|0|94|0||03047|근린생활시설|0|청운효자동||";

    assertThat(GeocodeLineParser.parse(line)).isEmpty();
  }

  @Test
  void 컬럼_부족_행은_skip() {
    assertThat(GeocodeLineParser.parse("11110|760|1111010100")).isEqualTo(Optional.empty());
  }
}
