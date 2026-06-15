package com.magampick.product.controller;

import com.magampick.global.security.CustomUserDetails;
import com.magampick.product.dto.MenuProductDetailResponse;
import com.magampick.product.service.ProductDetailQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소비자 일반 상품 조회 API. 기존 사장 전용 {@link ProductController} ({@code /api/v1/seller/stores/...}) 와 URL 충돌
 * 없음.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product (Consumer)", description = "소비자 일반 상품 조회 API")
public class ProductQueryController {

  private final ProductDetailQueryService productDetailQueryService;

  @GetMapping("/{productId}")
  @Operation(
      summary = "일반 상품 상세 조회",
      description =
          "일반 상품 단건 상세 정보. 매장 미리보기(거리·영업상태·closingTime) 포함. 평점/리뷰 수는 0(일반 상품은 주문 대상 아님). ROLE_CUSTOMER 인증 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "400", description = "기본 주소지 없음 (DEFAULT_ADDRESS_REQUIRED)"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "권한 없음 (ROLE_CUSTOMER 아님)"),
    @ApiResponse(responseCode = "404", description = "상품 없음 (PRODUCT_NOT_FOUND)")
  })
  public MenuProductDetailResponse detail(
      @PathVariable Long productId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return productDetailQueryService.getDetail(productId, userDetails.getUserId());
  }
}
