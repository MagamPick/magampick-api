package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "매장 등록 신청 요청")
public record StoreCreateRequest(
    @Schema(description = "매장명", example = "동네빵집") @NotBlank @Size(max = 50) String name,
    @Schema(description = "도로명 주소", example = "서울특별시 강남구 테헤란로 427") @NotBlank @Size(max = 200)
        String roadAddress,
    @Schema(description = "지번 주소 (선택)", example = "서울특별시 강남구 삼성동 159-1") @Size(max = 200)
        String jibunAddress,
    @Schema(description = "상세 주소 (선택)", example = "1층") @Size(max = 100) String detailAddress,
    @Schema(description = "우편번호", example = "06158") @NotBlank @Pattern(regexp = "\\d{5}")
        String zonecode,
    @Schema(description = "위도 (WGS84)", example = "37.5066") @NotNull Double latitude,
    @Schema(description = "경도 (WGS84)", example = "127.0535") @NotNull Double longitude,
    @Schema(description = "매장 전화번호", example = "0212345678") @NotBlank @Size(max = 20) String phone,
    @Schema(description = "매장 소개 (선택)", example = "매일 아침 직접 굽는 신선한 빵") @Size(max = 500)
        String description,
    @Schema(description = "카테고리 ID 목록 (1~3개)", example = "[1, 2]") @NotEmpty @Size(min = 1, max = 3)
        List<Long> categoryIds) {}
