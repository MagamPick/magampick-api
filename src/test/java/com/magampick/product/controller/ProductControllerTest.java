package com.magampick.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.security.CustomUserDetails;
import com.magampick.global.security.JwtAccessDeniedHandler;
import com.magampick.global.security.JwtAuthenticationEntryPoint;
import com.magampick.global.security.JwtProvider;
import com.magampick.global.security.Role;
import com.magampick.global.security.SecurityConfig;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.ProductCreateRequest;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.dto.ProductUpdateRequest;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.fixture.ProductFixture;
import com.magampick.product.service.ProductService;
import com.magampick.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ProductControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockitoBean ProductService productService;
  @MockitoBean JwtProvider jwtProvider;

  private static final CustomUserDetails SELLER_USER = new CustomUserDetails(1L, Role.SELLER);
  private static final CustomUserDetails CUSTOMER_USER = new CustomUserDetails(2L, Role.CUSTOMER);

  private MockMultipartFile requestPart(String json) {
    return new MockMultipartFile(
        "request", "request", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
  }

  private MockMultipartFile imagePart() {
    byte[] jpeg = new byte[1024];
    jpeg[0] = (byte) 0xFF;
    jpeg[1] = (byte) 0xD8;
    jpeg[2] = (byte) 0xFF;
    return new MockMultipartFile("image", "test.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg);
  }

  private String validCreateJson() throws Exception {
    return objectMapper.writeValueAsString(
        new ProductCreateRequest("크로아상", new BigDecimal("4500")));
  }

  private String validUpdateJson() throws Exception {
    return objectMapper.writeValueAsString(new ProductUpdateRequest("바게트", null));
  }

  // ── POST /api/v1/seller/stores/{storeId}/products ─────────────────────────

  @Test
  void POST_products_201_성공() throws Exception {
    given(productService.registerProduct(eq(1L), eq(10L), any(), any()))
        .willReturn(ProductFixture.aResponse(100L));

    mockMvc
        .perform(
            multipart("/api/v1/seller/stores/10/products")
                .file(requestPart(validCreateJson()))
                .file(imagePart())
                .with(user(SELLER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(100))
        .andExpect(jsonPath("$.data.name").value("크로아상"))
        .andExpect(jsonPath("$.data.status").value("ON_SALE"));
  }

  @Test
  void POST_products_400_가격_검증_실패() throws Exception {
    String invalidJson =
        objectMapper.writeValueAsString(new ProductCreateRequest("크로아상", new BigDecimal("0")));
    mockMvc
        .perform(
            multipart("/api/v1/seller/stores/10/products")
                .file(requestPart(invalidJson))
                .file(imagePart())
                .with(user(SELLER_USER)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void POST_products_401_미인증() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/seller/stores/10/products")
                .file(requestPart(validCreateJson()))
                .file(imagePart()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void POST_products_403_소비자_접근_거부() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/seller/stores/10/products")
                .file(requestPart(validCreateJson()))
                .file(imagePart())
                .with(user(CUSTOMER_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void POST_products_409_상품명_중복() throws Exception {
    given(productService.registerProduct(eq(1L), eq(10L), any(), any()))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATE));

    mockMvc
        .perform(
            multipart("/api/v1/seller/stores/10/products")
                .file(requestPart(validCreateJson()))
                .file(imagePart())
                .with(user(SELLER_USER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NAME_DUPLICATE"));
  }

  @Test
  void POST_products_201_이미지_없이_성공() throws Exception {
    given(productService.registerProduct(eq(1L), eq(10L), any(), isNull()))
        .willReturn(ProductFixture.aResponseWithoutImage(100L));

    mockMvc
        .perform(
            multipart("/api/v1/seller/stores/10/products")
                .file(requestPart(validCreateJson()))
                .with(user(SELLER_USER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(100));
  }

  // ── GET /api/v1/seller/stores/{storeId}/products ──────────────────────────

  @Test
  void GET_products_200_목록() throws Exception {
    PageResponse<ProductResponse> page =
        new PageResponse<>(List.of(ProductFixture.aResponse(100L)), 0, 20, 1L, 1, false, false);
    given(productService.getMyStoreProducts(eq(1L), eq(10L), any())).willReturn(page);

    mockMvc
        .perform(get("/api/v1/seller/stores/10/products").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(100))
        .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  void GET_products_403_타인_매장() throws Exception {
    given(productService.getMyStoreProducts(eq(1L), eq(10L), any()))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/products").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  // ── GET /api/v1/seller/stores/{storeId}/products/{productId} ──────────────

  @Test
  void GET_products_id_200_상세() throws Exception {
    given(productService.getMyStoreProduct(1L, 10L, 100L))
        .willReturn(ProductFixture.aResponse(100L));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/products/100").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(100));
  }

  @Test
  void GET_products_id_404_없음() throws Exception {
    given(productService.getMyStoreProduct(1L, 10L, 100L))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/products/100").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void GET_products_id_403_타인_매장() throws Exception {
    given(productService.getMyStoreProduct(1L, 10L, 100L))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(get("/api/v1/seller/stores/10/products/100").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  // ── PATCH /api/v1/seller/stores/{storeId}/products/{productId} ────────────

  @Test
  void PATCH_products_id_200_수정_성공() throws Exception {
    given(productService.updateProduct(eq(1L), eq(10L), eq(100L), any(), any()))
        .willReturn(ProductFixture.aResponse(100L));

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/v1/seller/stores/10/products/100")
                .file(requestPart(validUpdateJson()))
                .with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(100));
  }

  @Test
  void PATCH_products_id_409_이름_중복() throws Exception {
    given(productService.updateProduct(eq(1L), eq(10L), eq(100L), any(), any()))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATE));

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/v1/seller/stores/10/products/100")
                .file(requestPart(validUpdateJson()))
                .with(user(SELLER_USER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NAME_DUPLICATE"));
  }

  @Test
  void PATCH_products_id_404_없음() throws Exception {
    given(productService.updateProduct(eq(1L), eq(10L), eq(100L), any(), any()))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/v1/seller/stores/10/products/100")
                .file(requestPart(validUpdateJson()))
                .with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void PATCH_products_id_403_타인_매장() throws Exception {
    given(productService.updateProduct(eq(1L), eq(10L), eq(100L), any(), any()))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/v1/seller/stores/10/products/100")
                .file(requestPart(validUpdateJson()))
                .with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  @Test
  void PATCH_products_id_401_미인증() throws Exception {
    mockMvc
        .perform(
            multipart(HttpMethod.PATCH, "/api/v1/seller/stores/10/products/100")
                .file(requestPart(validUpdateJson())))
        .andExpect(status().isUnauthorized());
  }

  // ── DELETE /api/v1/seller/stores/{storeId}/products/{productId} ───────────

  @Test
  void DELETE_products_id_204_삭제_성공() throws Exception {
    willDoNothing().given(productService).deleteProduct(1L, 10L, 100L);

    mockMvc
        .perform(delete("/api/v1/seller/stores/10/products/100").with(user(SELLER_USER)))
        .andExpect(status().isNoContent());
  }

  @Test
  void DELETE_products_id_404_없음() throws Exception {
    willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND))
        .given(productService)
        .deleteProduct(1L, 10L, 100L);

    mockMvc
        .perform(delete("/api/v1/seller/stores/10/products/100").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void DELETE_products_id_401_미인증() throws Exception {
    mockMvc
        .perform(delete("/api/v1/seller/stores/10/products/100"))
        .andExpect(status().isUnauthorized());
  }

  // ── POST .../sold-out ─────────────────────────────────────────────────────

  @Test
  void POST_sold_out_200_품절_성공() throws Exception {
    given(productService.markSoldOut(1L, 10L, 100L))
        .willReturn(ProductFixture.aResponseWithStatus(100L, ProductStatus.SOLD_OUT));

    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/sold-out").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SOLD_OUT"));
  }

  @Test
  void POST_sold_out_404_없음() throws Exception {
    given(productService.markSoldOut(1L, 10L, 100L))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/sold-out").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void POST_sold_out_403_타인_매장() throws Exception {
    given(productService.markSoldOut(1L, 10L, 100L))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/sold-out").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  @Test
  void POST_sold_out_401_미인증() throws Exception {
    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/sold-out"))
        .andExpect(status().isUnauthorized());
  }

  // ── POST .../restock ──────────────────────────────────────────────────────

  @Test
  void POST_restock_200_재입고_성공() throws Exception {
    given(productService.restock(1L, 10L, 100L))
        .willReturn(ProductFixture.aResponseWithStatus(100L, ProductStatus.ON_SALE));

    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/restock").with(user(SELLER_USER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ON_SALE"));
  }

  @Test
  void POST_restock_404_없음() throws Exception {
    given(productService.restock(1L, 10L, 100L))
        .willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/restock").with(user(SELLER_USER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void POST_restock_403_타인_매장() throws Exception {
    given(productService.restock(1L, 10L, 100L))
        .willThrow(new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/restock").with(user(SELLER_USER)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));
  }

  @Test
  void POST_restock_401_미인증() throws Exception {
    mockMvc
        .perform(post("/api/v1/seller/stores/10/products/100/restock"))
        .andExpect(status().isUnauthorized());
  }
}
