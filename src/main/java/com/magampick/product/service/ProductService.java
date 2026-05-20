package com.magampick.product.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.storage.StorageService;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.ProductCreateRequest;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.dto.ProductUpdateRequest;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.mapper.ProductMapper;
import com.magampick.product.repository.ProductRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

  private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final ProductRepository productRepository;
  private final StoreRepository storeRepository;
  private final StorageService storageService;
  private final ProductMapper productMapper;

  @Transactional
  public ProductResponse registerProduct(
      Long sellerId, Long storeId, ProductCreateRequest request, MultipartFile image) {
    validateImage(image);

    Store store =
        storeRepository
            .findByIdAndSellerId(storeId, sellerId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    if (store.getStatus() != StoreStatus.APPROVED) {
      throw new BusinessException(StoreErrorCode.STORE_NOT_APPROVED);
    }

    if (productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(storeId, request.name())) {
      throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATE);
    }

    String imageUrl = hasImage(image) ? uploadProductImage(image) : null;

    Product product =
        Product.builder()
            .store(store)
            .name(request.name())
            .regularPrice(request.regularPrice())
            .imageUrl(imageUrl)
            .status(ProductStatus.ON_SALE)
            .build();
    productRepository.save(product);

    log.info("상품 등록됨. productId={}, storeId={}, sellerId={}", product.getId(), storeId, sellerId);
    return productMapper.toResponse(product);
  }

  public PageResponse<ProductResponse> getMyStoreProducts(
      Long sellerId, Long storeId, Pageable pageable) {
    verifyStoreOwnership(sellerId, storeId);
    Page<Product> page = productRepository.findByStoreIdAndDeletedAtIsNull(storeId, pageable);
    return PageResponse.of(page.map(productMapper::toResponse));
  }

  public ProductResponse getMyStoreProduct(Long sellerId, Long storeId, Long productId) {
    verifyStoreOwnership(sellerId, storeId);
    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    return productMapper.toResponse(product);
  }

  @Transactional
  public ProductResponse updateProduct(
      Long sellerId,
      Long storeId,
      Long productId,
      ProductUpdateRequest request,
      MultipartFile image) {
    validateImage(image);
    verifyStoreOwnership(sellerId, storeId);

    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    if (request.name() != null
        && productRepository.existsByStoreIdAndNameAndDeletedAtIsNullAndIdNot(
            storeId, request.name(), productId)) {
      throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATE);
    }

    String oldImageUrl = product.getImageUrl();
    String imageUrl = hasImage(image) ? uploadProductImage(image) : null;
    product.updateInfo(request.name(), request.regularPrice(), imageUrl);

    log.info(
        "상품 수정됨. productId={}, storeId={}, sellerId={}, oldImageUrl={}",
        productId,
        storeId,
        sellerId,
        oldImageUrl);
    return productMapper.toResponse(product);
  }

  @Transactional
  public void deleteProduct(Long sellerId, Long storeId, Long productId) {
    verifyStoreOwnership(sellerId, storeId);

    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    product.softDelete();
    log.info("상품 삭제됨. productId={}, storeId={}, sellerId={}", productId, storeId, sellerId);
  }

  @Transactional
  public ProductResponse markSoldOut(Long sellerId, Long storeId, Long productId) {
    verifyStoreOwnership(sellerId, storeId);

    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    product.markSoldOut();
    log.info("상품 품절 처리됨. productId={}, storeId={}, sellerId={}", productId, storeId, sellerId);
    return productMapper.toResponse(product);
  }

  @Transactional
  public ProductResponse restock(Long sellerId, Long storeId, Long productId) {
    verifyStoreOwnership(sellerId, storeId);

    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    product.restock();
    log.info("상품 재입고 처리됨. productId={}, storeId={}, sellerId={}", productId, storeId, sellerId);
    return productMapper.toResponse(product);
  }

  private void verifyStoreOwnership(Long sellerId, Long storeId) {
    storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
  }

  private String uploadProductImage(MultipartFile image) {
    try {
      return storageService.upload(image);
    } catch (BusinessException e) {
      throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_UPLOAD_FAILED, e);
    }
  }

  private void validateImage(MultipartFile image) {
    if (!hasImage(image)) {
      return;
    }
    if (image.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_TOO_LARGE);
    }
    String contentType = image.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
    }
    validateImageMagicBytes(image);
  }

  // Content-Type 헤더 스푸핑 방어 — 실제 파일 시그니처로 검증
  private void validateImageMagicBytes(MultipartFile image) {
    try (InputStream is = image.getInputStream()) {
      byte[] header = is.readNBytes(12);
      if (!isJpeg(header) && !isPng(header) && !isWebp(header)) {
        throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (IOException e) {
      throw new BusinessException(ProductErrorCode.PRODUCT_IMAGE_INVALID_TYPE);
    }
  }

  private boolean isJpeg(byte[] h) {
    return h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
  }

  private boolean isPng(byte[] h) {
    return h.length >= 4
        && (h[0] & 0xFF) == 0x89
        && (h[1] & 0xFF) == 0x50
        && (h[2] & 0xFF) == 0x4E
        && (h[3] & 0xFF) == 0x47;
  }

  // WebP: "RIFF"(0-3) + 파일크기(4-7) + "WEBP"(8-11)
  private boolean isWebp(byte[] h) {
    return h.length >= 12
        && (h[0] & 0xFF) == 0x52
        && (h[1] & 0xFF) == 0x49
        && (h[2] & 0xFF) == 0x46
        && (h[3] & 0xFF) == 0x46
        && (h[8] & 0xFF) == 0x57
        && (h[9] & 0xFF) == 0x45
        && (h[10] & 0xFF) == 0x42
        && (h[11] & 0xFF) == 0x50;
  }

  private boolean hasImage(MultipartFile image) {
    return image != null && !image.isEmpty();
  }
}
