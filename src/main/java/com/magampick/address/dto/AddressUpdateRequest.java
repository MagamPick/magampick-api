package com.magampick.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 주소지 부분 수정 요청. 모든 필드 optional — 명시된 필드만 갱신. null = 수정 안 함. 기본 주소지 변경 (isDefault) 은 별도 endpoint 사용.
 *
 * <p>{@code @NotBlank} 대신 {@code @Size(min=1)} 만 사용하는 이유: {@code @NotBlank} 는 null 도 거부해 partial
 * update 의 "필드 미전송" 케이스를 막아버린다. 공백만 ({@code " "}) 같은 abuse 입력은 DB CHECK 제약이 차단한다.
 */
@Schema(description = "주소지 수정 요청 (부분 수정, 모든 필드 optional)")
public record AddressUpdateRequest(
    @Schema(description = "사용자 지정 라벨", example = "회사") @Size(min = 1, max = 20) String label,
    @Schema(description = "도로명 주소") @Size(min = 1, max = 200) String roadAddress,
    @Schema(description = "지번 주소") @Size(max = 200) String jibunAddress,
    @Schema(description = "상세 주소") @Size(max = 100) String detailAddress,
    @Schema(description = "우편번호 5자리") @Pattern(regexp = "^[0-9]{5}$") String zonecode,
    @Schema(description = "위도. longitude 와 쌍으로 함께 전송") @DecimalMin("-90") @DecimalMax("90")
        Double latitude,
    @Schema(description = "경도. latitude 와 쌍으로 함께 전송") @DecimalMin("-180") @DecimalMax("180")
        Double longitude) {

  /** 좌표는 lat/lng 쌍으로 함께 전송하거나 둘 다 미전송. 한쪽만 보내면 검증 실패. */
  @AssertTrue(message = "latitude 와 longitude 는 함께 전송해야 합니다")
  public boolean isCoordinatePairValid() {
    return (latitude == null) == (longitude == null);
  }
}
