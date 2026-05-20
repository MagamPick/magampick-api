package com.magampick.product.controller;

import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.product.dto.ProductCreateRequest;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.dto.ProductUpdateRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

  @PatchMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "일반 상품 수정", description = "본인 매장 상품의 이름·가격·이미지를 부분 수정한다. null 필드는 변경하지 않는다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 이미지 규격 위반"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근"),
    @ApiResponse(responseCode = "404", description = "상품 없음"),
    @ApiResponse(responseCode = "409", description = "상품명 중복")
  })
  public ProductResponse update(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PathVariable Long productId,
      @RequestPart("request") @Valid ProductUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    return productService.updateProduct(
        userDetails.getUserId(), storeId, productId, request, image);
  }

  @DeleteMapping("/{productId}")
  @Operation(summary = "일반 상품 삭제", description = "본인 매장 상품을 소프트 삭제한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근"),
    @ApiResponse(responseCode = "404", description = "상품 없음")
  })
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PathVariable Long productId) {
    productService.deleteProduct(userDetails.getUserId(), storeId, productId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{productId}/sold-out")
  @Operation(summary = "상품 품절 처리", description = "상품을 SOLD_OUT 상태로 변경한다. 이미 품절이면 변경 없이 200 반환.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근"),
    @ApiResponse(responseCode = "404", description = "상품 없음")
  })
  public ProductResponse soldOut(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PathVariable Long productId) {
    return productService.markSoldOut(userDetails.getUserId(), storeId, productId);
  }

  @PostMapping("/{productId}/restock")
  @Operation(summary = "상품 재입고 처리", description = "상품을 ON_SALE 상태로 변경한다. 이미 판매 중이면 변경 없이 200 반환.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "처리 성공"),
    @ApiResponse(responseCode = "401", description = "미인증"),
    @ApiResponse(responseCode = "403", description = "타인 매장 접근"),
    @ApiResponse(responseCode = "404", description = "상품 없음")
  })
  public ProductResponse restock(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long storeId,
      @PathVariable Long productId) {
    return productService.restock(userDetails.getUserId(), storeId, productId);
  }
}
