package com.magampick.store.service;

import com.magampick.global.common.GeometryUtil;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.global.storage.StorageService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.config.StoreProperties;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreCategory;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.dto.StoreAdminDetailResponse;
import com.magampick.store.dto.StoreAdminResponse;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.mapper.StoreMapper;
import com.magampick.store.repository.StoreCategoryRepository;
import com.magampick.store.repository.StoreRepository;
import java.util.List;
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
public class StoreService {

  private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final StoreRepository storeRepository;
  private final StoreCategoryRepository storeCategoryRepository;
  private final SellerRepository sellerRepository;
  private final StorageService storageService;
  private final BusinessVerificationService businessVerificationService;
  private final StoreMapper storeMapper;
  private final StoreProperties storeProperties;

  @Transactional
  public StoreRegisterResponse registerStore(
      Long sellerId, StoreCreateRequest request, MultipartFile image) {
    validateImage(image);

    Seller seller =
        sellerRepository
            .findById(sellerId)
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new BusinessException(SellerErrorCode.SELLER_NOT_FOUND));

    businessVerificationService.verify(seller.getBusinessNumber());

    List<StoreCategory> categories = storeCategoryRepository.findAllById(request.categoryIds());
    if (categories.size() != request.categoryIds().size()) {
      throw new BusinessException(StoreErrorCode.STORE_CATEGORY_NOT_FOUND);
    }

    String imageUrl = uploadStoreImage(image);

    StoreStatus status = storeProperties.autoApprove() ? StoreStatus.APPROVED : StoreStatus.PENDING;

    Store store =
        Store.builder()
            .seller(seller)
            .name(request.name())
            .roadAddress(request.roadAddress())
            .jibunAddress(request.jibunAddress())
            .detailAddress(request.detailAddress())
            .zonecode(request.zonecode())
            .location(GeometryUtil.toPoint(request.latitude(), request.longitude()))
            .phone(request.phone())
            .description(request.description())
            .imageUrl(imageUrl)
            .status(status)
            .categories(categories)
            .build();

    storeRepository.save(store);
    log.info(
        "store registered. storeId={}, sellerId={}, status={}", store.getId(), sellerId, status);
    return new StoreRegisterResponse(store.getId(), store.getStatus());
  }

  @Transactional(readOnly = true)
  public List<StoreResponse> getMyStores(Long sellerId) {
    return storeRepository.findBySellerId(sellerId).stream().map(storeMapper::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public StoreDetailResponse getMyStore(Long sellerId, Long storeId) {
    Store store =
        storeRepository
            .findByIdAndSellerId(storeId, sellerId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
    return storeMapper.toDetailResponse(store);
  }

  @Transactional(readOnly = true)
  public PageResponse<StoreAdminResponse> getStoresForAdmin(StoreStatus status, Pageable pageable) {
    Page<Store> page = storeRepository.findByStatusFilter(status, pageable);
    return PageResponse.of(page.map(storeMapper::toAdminResponse));
  }

  @Transactional(readOnly = true)
  public StoreAdminDetailResponse getStoreForAdmin(Long storeId) {
    Store store =
        storeRepository
            .findByIdWithCategories(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
    return storeMapper.toAdminDetailResponse(store);
  }

  @Transactional
  public void approveStore(Long storeId) {
    Store store =
        storeRepository
            .findById(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
    store.approve();
    log.info("store approved. storeId={}", storeId);
  }

  @Transactional
  public void rejectStore(Long storeId, String rejectionReason) {
    Store store =
        storeRepository
            .findById(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));
    store.reject(rejectionReason);
    log.info("store rejected. storeId={}, reason={}", storeId, rejectionReason);
  }

  private String uploadStoreImage(MultipartFile image) {
    try {
      return storageService.upload(image);
    } catch (BusinessException e) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_UPLOAD_FAILED, e);
    }
  }

  private void validateImage(MultipartFile image) {
    if (image.isEmpty()) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_INVALID_TYPE);
    }
    if (image.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_TOO_LARGE);
    }
    String contentType = image.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(StoreErrorCode.STORE_IMAGE_INVALID_TYPE);
    }
  }
}
