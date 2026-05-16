package com.magampick.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    @Schema(description = "위도", example = "37.5066") @NotNull @DecimalMin("-90") @DecimalMax("90")
        Double latitude,
    @Schema(description = "경도", example = "127.0535")
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        Double longitude) {}
