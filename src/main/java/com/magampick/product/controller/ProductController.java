package com.magampick.product.controller;

import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.product.dto.ProductCreateRequest;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/seller/stores/{storeId}/products")
@RequiredArgsConstructor
@Tag(name = "Product (Seller)", description = "사장 일반 상품 관리 API")
public class ProductController {

  private final ProductService productService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "일반 상품 등록", description = "본인 매장에 일반 상품을 등록한다. 매장이 APPROVED 상태여야 한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 이미지 규격 위반"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근 또는 매장 미승인"),
    @ApiResponse(responseCode = "409", description = "상품명 중복")
  })
  public ResponseEntity<ProductResponse> register(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @RequestPart("request") @Valid ProductCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    ProductResponse response =
        productService.registerProduct(userDetails.getUserId(), storeId, request, image);
    return ResponseEntity.created(
            URI.create("/api/v1/seller/stores/" + storeId + "/products/" + response.id()))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "본인 매장 상품 목록 조회", description = "본인 매장의 일반 상품 목록을 페이지네이션으로 조회한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근")
  })
  public PageResponse<ProductResponse> list(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return productService.getMyStoreProducts(userDetails.getUserId(), storeId, pageable);
  }

  @GetMapping("/{productId}")
  @Operation(summary = "본인 매장 상품 상세 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근"),
    @ApiResponse(responseCode = "404", description = "상품 없음")
  })
  public ProductResponse detail(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PathVariable Long productId) {
    return productService.getMyStoreProduct(userDetails.getUserId(), storeId, productId);
  }
}
