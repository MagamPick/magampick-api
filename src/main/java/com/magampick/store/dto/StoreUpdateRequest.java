package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 매장 정보 수정 요청 (부분 수정 — null = 변경 X). 사업자번호·영업상태·영업시간은 비범위 (수정 폼에서 무시). 이미지는 multipart 의 별도 part 로
 * 전달.
 */
@Schema(description = "매장 정보 수정 요청 (부분 수정, null = 변경 X)")
public record StoreUpdateRequest(
    @Schema(description = "매장명 (변경 시)", example = "동네빵집") @Size(max = 50) String name,
    @Schema(description = "도로명 주소 (변경 시 — 지오코딩 재호출 트리거)", example = "서울 강남구 테헤란로 427")
        @Size(max = 200)
        String roadAddress,
    @Schema(description = "지번 주소 (선택, 주소 변경 시 함께)", example = "삼성동 159-1") @Size(max = 200)
        String jibunAddress,
    @Schema(description = "상세 주소 (선택)", example = "1층") @Size(max = 100) String detailAddress,
    @Schema(description = "우편번호 (5자리, 주소 변경 시 함께)", example = "06158") @Pattern(regexp = "\\d{5}")
        String zonecode,
    @Schema(description = "매장 전화번호", example = "0212345678") @Size(max = 20) String phone,
    @Schema(description = "매장 소개", example = "매일 아침 직접 굽는 신선한 빵") @Size(max = 500)
        String description) {}
