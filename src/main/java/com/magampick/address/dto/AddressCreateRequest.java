package com.magampick.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 주소지 등록 요청. 좌표 출처는 두 경로 중 하나.
 *
 * <ul>
 *   <li>검색 경로 (다음 위젯): {@code sigunguCode} + {@code roadnameCode} → 서버 정방향 지오코딩
 *   <li>GPS 경로 (현재 위치): {@code latitude} + {@code longitude} → raw 좌표 직접 저장 (지오코딩 생략)
 * </ul>
 *
 * <p>역지오코딩은 도로명만 주고 코드는 주지 않으므로 GPS 경로에서는 코드가 비어 있다. {@code roadAddress} 는 두 경로 모두 라벨로 저장된다.
 */
@Schema(description = "주소지 등록 요청")
public record AddressCreateRequest(
    @Schema(description = "사용자 지정 라벨", example = "집") @NotBlank @Size(min = 1, max = 20)
        String label,
    @Schema(description = "도로명 주소", example = "서울특별시 강남구 테헤란로 427")
        @NotBlank
        @Size(min = 1, max = 200)
        String roadAddress,
    @Schema(description = "지번 주소 (선택)", example = "서울특별시 강남구 삼성동 159") @Size(max = 200)
        String jibunAddress,
    @Schema(description = "상세 주소 (사용자 직접 입력)", example = "101동 1502호") @Size(max = 100)
        String detailAddress,
    @Schema(description = "우편번호 5자리", example = "06158") @Pattern(regexp = "^[0-9]{5}$")
        String zonecode,
    @Schema(description = "시군구코드 (검색 경로: 다음 위젯 sigunguCode, 5자리. GPS 경로는 생략)", example = "11680")
        @Pattern(regexp = "\\d{5}")
        String sigunguCode,
    @Schema(
            description = "도로명번호 (검색 경로: 다음 위젯 roadnameCode, 최대 7자리. GPS 경로는 생략)",
            example = "3179999")
        @Pattern(regexp = "\\d{1,7}")
        String roadnameCode,
    @Schema(description = "위도 (GPS 경로: 현재 위치 좌표 직접 저장. 검색 경로는 생략)", example = "37.5066")
        @DecimalMin("-90")
        @DecimalMax("90")
        Double latitude,
    @Schema(description = "경도 (GPS 경로: 현재 위치 좌표 직접 저장. 검색 경로는 생략)", example = "127.0535")
        @DecimalMin("-180")
        @DecimalMax("180")
        Double longitude) {

  /**
   * 좌표를 만들려면 GPS 경로 (latitude+longitude) 또는 검색 경로 (sigunguCode+roadnameCode) 중 한 쌍은 필수. 둘 다 없으면 좌표를
   * 확보할 수 없으므로 거부한다.
   */
  @AssertTrue(message = "위경도(GPS) 또는 시군구코드+도로명번호(검색) 중 한 쌍은 필수입니다")
  public boolean isLocationSourceValid() {
    boolean hasCoordinates = latitude != null && longitude != null;
    boolean hasGeocodeKey = sigunguCode != null && roadnameCode != null;
    return hasCoordinates || hasGeocodeKey;
  }
}
