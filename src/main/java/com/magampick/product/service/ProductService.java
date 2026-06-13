package com.magampick.product.service;

import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.repository.ClearanceItemRepository;
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
import com.magampick.store.service.StoreService;
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
  private final StoreService storeService;
  private final StorageService storageService;
  private final ProductMapper productMapper;
  private final ClearanceItemRepository clearanceItemRepository;

  @Transactional
  public ProductResponse registerProduct(
      Long sellerId, Long storeId, ProductCreateRequest request, MultipartFile image) {
    // 이미지 검증
    validateImage(image);

    // 소유권 확인
    Store store = storeService.requireOwnedStore(sellerId, storeId);

    // 상품명 trim (공백 변형 우회 방지 — 비교·저장 모두 trim 값 사용)
    String name = request.name().trim();

    // 상품명 중복 확인
    if (productRepository.existsByStoreIdAndNameAndDeletedAtIsNull(storeId, name)) {
      throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATE);
    }

    // 이미지 업로드
    String imageUrl = hasImage(image) ? uploadProductImage(image) : null;

    // 상품 생성 및 저장
    Product product =
        Product.builder()
            .store(store)
            .name(name)
            .regularPrice(request.regularPrice())
            .imageUrl(imageUrl)
            .status(request.status() != null ? request.status() : ProductStatus.ON_SALE)
            .category(request.category())
            .description(request.description())
            .build();
    productRepository.save(product);

    log.info("상품 등록됨. productId={}, storeId={}, sellerId={}", product.getId(), storeId, sellerId);
    return productMapper.toResponse(product);
  }

  public PageResponse<ProductResponse> getMyStoreProducts(
      Long sellerId, Long storeId, Pageable pageable) {
    storeService.requireOwnedStore(sellerId, storeId);
    Page<Product> page = productRepository.findByStoreIdAndDeletedAtIsNull(storeId, pageable);
    return PageResponse.of(page.map(productMapper::toResponse));
  }

  public ProductResponse getMyStoreProduct(Long sellerId, Long storeId, Long productId) {
    storeService.requireOwnedStore(sellerId, storeId);
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
    // 이미지 검증
    validateImage(image);
    // 소유권 확인
    storeService.requireOwnedStore(sellerId, storeId);

    // 상품 조회
    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    // 상품명 trim (공백 변형 우회 방지 — null 이면 미수정)
    String name = request.name() != null ? request.name().trim() : null;

    // 상품명 중복 확인
    if (name != null
        && productRepository.existsByStoreIdAndNameAndDeletedAtIsNullAndIdNot(
            storeId, name, productId)) {
      throw new BusinessException(ProductErrorCode.PRODUCT_NAME_DUPLICATE);
    }

    // 이미지 업로드 및 상품 수정
    String oldImageUrl = product.getImageUrl();
    String imageUrl = hasImage(image) ? uploadProductImage(image) : null;
    product.updateInfo(
        name,
        request.regularPrice(),
        imageUrl,
        request.description(),
        request.category(),
        request.status());

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
    // 소유권 확인
    storeService.requireOwnedStore(sellerId, storeId);

    // 상품 조회
    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    // 활성 떨이 확인
    if (clearanceItemRepository.existsByProductIdAndStatus(productId, ClearanceItemStatus.OPEN)) {
      throw new BusinessException(ProductErrorCode.PRODUCT_HAS_ACTIVE_CLEARANCE);
    }

    // 소프트 삭제
    product.softDelete();
    log.info("상품 삭제됨. productId={}, storeId={}, sellerId={}", productId, storeId, sellerId);
  }

  @Transactional
  public ProductResponse markSoldOut(Long sellerId, Long storeId, Long productId) {
    // 소유권 확인
    storeService.requireOwnedStore(sellerId, storeId);

    // 상품 조회
    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    // 품절 처리
    product.markSoldOut();
    log.info("상품 품절 처리됨. productId={}, storeId={}, sellerId={}", productId, storeId, sellerId);
    return productMapper.toResponse(product);
  }

  @Transactional
  public ProductResponse restock(Long sellerId, Long storeId, Long productId) {
    // 소유권 확인
    storeService.requireOwnedStore(sellerId, storeId);

    // 상품 조회
    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(productId, storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    // 재입고 처리
    product.restock();
    log.info("상품 재입고 처리됨. productId={}, storeId={}, sellerId={}", productId, storeId, sellerId);
    return productMapper.toResponse(product);
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
