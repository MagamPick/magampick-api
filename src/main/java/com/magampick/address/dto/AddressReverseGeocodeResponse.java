package com.magampick.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 위치 역지오코딩 응답")
public record AddressReverseGeocodeResponse(
    @Schema(description = "가장 가까운 도로명 주소", example = "서울특별시 중구 세종대로 110") String roadAddress) {}
