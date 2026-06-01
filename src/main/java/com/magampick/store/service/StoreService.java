package com.magampick.store.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.global.storage.StorageService;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.repository.SellerRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.dto.StoreCreateRequest;
import com.magampick.store.dto.StoreDetailResponse;
import com.magampick.store.dto.StoreRegisterResponse;
import com.magampick.store.dto.StoreResponse;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.mapper.StoreMapper;
import com.magampick.store.repository.StoreRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
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
  private final SellerRepository sellerRepository;
  private final StorageService storageService;
  private final BusinessVerificationService businessVerificationService;
  private final GeocodingService geocodingService;
  private final StoreMapper storeMapper;

  /**
   * 매장 등록 (경로 B — 로그인 사장의 독립 등록). 국세청 검증·지오코딩·이미지 업로드를 통과하면 자동 승인으로 즉시 생성된다.
   *
   * <p>외부 호출(검증·지오코딩·업로드)은 트랜잭션 시작 전에 처리하고 결과만 단일 INSERT(save) 의 트랜잭션으로 반영한다 — 느린 외부 호출 동안 DB 트랜잭션을
   * 잡지 않기 위함. 가입 통합(경로 A) 의 다중 엔티티 롤백은 별도 작업.
   */
  public StoreRegisterResponse registerStore(
      Long sellerId, StoreCreateRequest request, MultipartFile image) {
    String businessNumber = normalizeBusinessNumber(request.businessNumber());
    validateImage(image);

    businessVerificationService.verify(businessNumber);
    Point location = geocodingService.geocode(request.roadAddress());
    String imageUrl = uploadStoreImage(image);

    Seller seller =
        sellerRepository
            .findById(sellerId)
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new BusinessException(SellerErrorCode.SELLER_NOT_FOUND));

    Store store =
        storeRepository.save(
            Store.builder()
                .seller(seller)
                .businessNumber(businessNumber)
                .name(request.name())
                .roadAddress(request.roadAddress())
                .jibunAddress(request.jibunAddress())
                .detailAddress(request.detailAddress())
                .zonecode(request.zonecode())
                .location(location)
                .phone(request.phone())
                .description(request.description())
                .imageUrl(imageUrl)
                .build());

    log.info("매장 등록됨. storeId={}, sellerId={}", store.getId(), sellerId);
    return new StoreRegisterResponse(store.getId());
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

  private String normalizeBusinessNumber(String raw) {
    String digits = raw == null ? "" : raw.replace("-", "");
    if (!digits.matches("\\d{10}")) {
      throw new BusinessException(StoreErrorCode.BUSINESS_NUMBER_FORMAT_INVALID);
    }
    return digits;
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
