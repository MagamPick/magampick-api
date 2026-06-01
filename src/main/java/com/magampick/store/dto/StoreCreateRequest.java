package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "매장 등록 신청 요청")
public record StoreCreateRequest(
    @Schema(description = "사업자 번호 (숫자 10자리, 하이픈 허용)", example = "123-45-67890")
        @NotBlank
        @Size(max = 20)
        String businessNumber,
    @Schema(description = "매장명", example = "동네빵집") @NotBlank @Size(max = 50) String name,
    @Schema(description = "도로명 주소", example = "서울특별시 강남구 테헤란로 427") @NotBlank @Size(max = 200)
        String roadAddress,
    @Schema(description = "지번 주소 (선택)", example = "서울특별시 강남구 삼성동 159-1") @Size(max = 200)
        String jibunAddress,
    @Schema(description = "상세 주소 (선택)", example = "1층") @Size(max = 100) String detailAddress,
    @Schema(description = "우편번호", example = "06158") @NotBlank @Pattern(regexp = "\\d{5}")
        String zonecode,
    @Schema(description = "매장 전화번호", example = "0212345678") @NotBlank @Size(max = 20) String phone,
    @Schema(description = "매장 소개 (선택)", example = "매일 아침 직접 굽는 신선한 빵") @Size(max = 500)
        String description) {}
