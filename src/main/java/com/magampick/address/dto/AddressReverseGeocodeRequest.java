package com.magampick.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "현재 위치 역지오코딩 요청")
public record AddressReverseGeocodeRequest(
    @Schema(description = "위도", example = "37.5665") @NotNull @DecimalMin("-90") @DecimalMax("90")
        Double latitude,
    @Schema(description = "경도", example = "126.9780")
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        Double longitude) {}
