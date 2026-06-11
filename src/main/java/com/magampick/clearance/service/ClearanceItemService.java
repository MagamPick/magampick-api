package com.magampick.clearance.service;

import com.magampick.clearance.domain.ClearanceCloseReason;
import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.ClearanceItemCreateRequest;
import com.magampick.clearance.dto.ClearanceItemResponse;
import com.magampick.clearance.dto.ClearanceItemUpdateRequest;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.mapper.ClearanceItemMapper;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.product.domain.Product;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.service.StoreService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClearanceItemService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ClearanceItemRepository clearanceItemRepository;
  private final ProductRepository productRepository;
  private final StoreService storeService;
  private final ClearanceItemMapper clearanceItemMapper;
  private final ClearanceNotificationService clearanceNotificationService;

  @Transactional
  public ClearanceItemResponse registerClearanceItem(
      Long sellerId, Long storeId, ClearanceItemCreateRequest request) {
    // 소유권 확인
    Store store = storeService.requireOwnedStore(sellerId, storeId);

    // 상품 조회
    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(request.productId(), storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    // 판매 가능 상태 확인
    if (!product.isOnSale()) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_PRODUCT_NOT_ON_SALE);
    }

    // 중복 떨이 확인
    if (clearanceItemRepository.existsByProductIdAndStatus(
        request.productId(), ClearanceItemStatus.OPEN)) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_OPEN_EXISTS);
    }

    // 판매가 검증
    if (request.salePrice().compareTo(product.getRegularPrice()) >= 0) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED);
    }

    // 픽업 시간 검증
    LocalDate today = LocalDate.now(KST);
    LocalDateTime nowKst = LocalDateTime.now(KST);
    if (!request.pickupEndAt().toLocalDate().equals(today)
        || !request.pickupEndAt().isAfter(nowKst)) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_INVALID_PICKUP_WINDOW);
    }

    // 떨이 생성 및 저장
    ClearanceItem item =
        ClearanceItem.builder()
            .store(store)
            .product(product)
            .name(product.getName())
            .regularPrice(product.getRegularPrice())
            .salePrice(request.salePrice())
            .totalQuantity(request.totalQuantity())
            .pickupStartAt(LocalDateTime.now(KST))
            .pickupEndAt(request.pickupEndAt())
            .build();
    try {
      clearanceItemRepository.saveAndFlush(item);
    } catch (DataIntegrityViolationException e) {
      // 동시 요청 race condition 대비 — uq_clearance_items_product_open 위반 시 409
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_OPEN_EXISTS);
    }

    log.info(
        "마감 임박 상품 등록됨. clearanceItemId={}, productId={}, storeId={}, sellerId={}",
        item.getId(),
        request.productId(),
        storeId,
        sellerId);
    // 알림 발송
    clearanceNotificationService.notifyNewClearanceItem(item);
    return clearanceItemMapper.toResponse(item);
  }

  public PageResponse<ClearanceItemResponse> getMyClearanceItems(
      Long sellerId, Long storeId, Pageable pageable) {
    storeService.requireOwnedStore(sellerId, storeId);
    Page<ClearanceItem> page = clearanceItemRepository.findByStoreId(storeId, pageable);
    return PageResponse.of(page.map(clearanceItemMapper::toResponse));
  }

  public ClearanceItemResponse getMyClearanceItem(
      Long sellerId, Long storeId, Long clearanceItemId) {
    storeService.requireOwnedStore(sellerId, storeId);
    ClearanceItem item =
        clearanceItemRepository
            .findByIdAndStoreId(clearanceItemId, storeId)
            .orElseThrow(
                () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));
    return clearanceItemMapper.toResponse(item);
  }

  @Transactional
  public ClearanceItemResponse updateClearanceItem(
      Long sellerId, Long storeId, Long clearanceItemId, ClearanceItemUpdateRequest request) {
    // 소유권 확인
    storeService.requireOwnedStore(sellerId, storeId);

    // 떨이 조회
    ClearanceItem item =
        clearanceItemRepository
            .findByIdAndStoreId(clearanceItemId, storeId)
            .orElseThrow(
                () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));

    // 상태 검증
    if (!item.isOpen()) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_OPEN);
    }

    // 판매가 검증
    if (request.salePrice() != null && request.salePrice().compareTo(item.getRegularPrice()) >= 0) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED);
    }

    // 픽업 시간 검증
    if (request.pickupEndAt() != null) {
      LocalDate today = LocalDate.now(KST);
      if (!request.pickupEndAt().toLocalDate().equals(today)
          || !request.pickupEndAt().isAfter(LocalDateTime.now(KST))) {
        throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_INVALID_PICKUP_WINDOW);
      }
    }

    // 떨이 수정
    item.update(request.salePrice(), request.totalQuantity(), request.pickupEndAt());

    log.info(
        "마감 임박 상품 수정됨. clearanceItemId={}, storeId={}, sellerId={}",
        clearanceItemId,
        storeId,
        sellerId);
    return clearanceItemMapper.toResponse(item);
  }

  @Transactional
  public ClearanceItemResponse closeClearanceItem(
      Long sellerId, Long storeId, Long clearanceItemId) {
    // 소유권 확인
    storeService.requireOwnedStore(sellerId, storeId);

    // 떨이 조회
    ClearanceItem item =
        clearanceItemRepository
            .findByIdAndStoreId(clearanceItemId, storeId)
            .orElseThrow(
                () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));

    // 수동 마감 처리
    if (!item.isClosed()) {
      item.close(ClearanceCloseReason.MANUAL);
      log.info(
          "마감 임박 상품 수동 마감됨. clearanceItemId={}, storeId={}, sellerId={}",
          clearanceItemId,
          storeId,
          sellerId);
    }

    return clearanceItemMapper.toResponse(item);
  }

  @Transactional
  public int autoCloseExpiredItems(LocalDateTime now) {
    return clearanceItemRepository.closeExpiredItems(now);
  }
}
