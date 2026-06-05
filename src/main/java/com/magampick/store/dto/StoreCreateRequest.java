package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "매장 등록 신청 요청 — 사업자 진위확인 3요소 + 매장 정보")
public record StoreCreateRequest(
    @Schema(description = "사업자 번호 (숫자 10자리, 하이픈 허용)", example = "123-45-67890")
        @NotBlank
        @Size(max = 20)
        String businessNumber,
    @Schema(description = "대표자 실명 (사업자등록증 기재)", example = "홍길동") @NotBlank @Size(max = 30)
        String representativeName,
    @Schema(description = "개업일자 (사업자등록증 기재, ISO yyyy-MM-dd)", example = "2024-03-15") @NotNull
        LocalDate openDate,
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
        String description,
    // 다음 우편번호 위젯 반환값 — 정방향 지오코딩 자연키 조립용 (도로명코드 = sigunguCode + roadnameCode)
    @Schema(description = "시군구코드 (다음 위젯 sigunguCode, 5자리)", example = "11680")
        @NotBlank
        @Pattern(regexp = "\\d{5}")
        String sigunguCode,
    @Schema(description = "도로명번호 (다음 위젯 roadnameCode, 최대 7자리)", example = "3179999")
        @NotBlank
        @Pattern(regexp = "\\d{1,7}")
        String roadnameCode) {}
