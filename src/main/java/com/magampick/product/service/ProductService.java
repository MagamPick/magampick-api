package com.magampick.product.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.storage.StorageService;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.dto.ProductCreateRequest;
import com.magampick.product.dto.ProductResponse;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.mapper.ProductMapper;
import com.magampick.product.repository.ProductRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
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

    if (productRepository.existsByStoreIdAndName(storeId, request.name())) {
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

  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> getMyStoreProducts(
      Long sellerId, Long storeId, Pageable pageable) {
    verifyStoreOwnership(sellerId, storeId);
    Page<Product> page = productRepository.findByStoreId(storeId, pageable);
    return PageResponse.of(page.map(productMapper::toResponse));
  }

  @Transactional(readOnly = true)
  public ProductResponse getMyStoreProduct(Long sellerId, Long storeId, Long productId) {
    verifyStoreOwnership(sellerId, storeId);
    Product product =
        productRepository
            .findByIdAndStoreId(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
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
  }

  private boolean hasImage(MultipartFile image) {
    return image != null && !image.isEmpty();
  }
}
