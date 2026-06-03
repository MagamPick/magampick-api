package com.magampick.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "사업자 진위확인 요청 (등록 폼 [조회하기] 버튼)")
public record BusinessVerificationRequest(
    @Schema(description = "사업자 번호 (숫자 10자리, 하이픈 허용)", example = "123-45-67890")
        @NotBlank
        @Size(max = 20)
        String businessNumber,
    @Schema(description = "대표자 실명 (사업자등록증 기재)", example = "홍길동") @NotBlank @Size(max = 30)
        String representativeName,
    @Schema(description = "개업일자 (사업자등록증 기재, ISO yyyy-MM-dd)", example = "2024-03-15") @NotNull
        LocalDate openDate) {}
