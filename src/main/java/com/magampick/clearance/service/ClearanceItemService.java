package com.magampick.clearance.service;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import com.magampick.clearance.dto.ClearanceItemCreateRequest;
import com.magampick.clearance.dto.ClearanceItemResponse;
import com.magampick.clearance.exception.ClearanceItemErrorCode;
import com.magampick.clearance.mapper.ClearanceItemMapper;
import com.magampick.clearance.repository.ClearanceItemRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import com.magampick.product.exception.ProductErrorCode;
import com.magampick.product.repository.ProductRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreStatus;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.time.LocalDate;
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
  private final StoreRepository storeRepository;
  private final ClearanceItemMapper clearanceItemMapper;

  @Transactional
  public ClearanceItemResponse registerClearanceItem(
      Long sellerId, Long storeId, ClearanceItemCreateRequest request) {
    Store store =
        storeRepository
            .findByIdAndSellerId(storeId, sellerId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));

    if (store.getStatus() != StoreStatus.APPROVED) {
      throw new BusinessException(StoreErrorCode.STORE_NOT_APPROVED);
    }

    Product product =
        productRepository
            .findByIdAndStoreIdAndDeletedAtIsNull(request.productId(), storeId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    if (product.getStatus() != ProductStatus.ON_SALE) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_PRODUCT_NOT_ON_SALE);
    }

    if (clearanceItemRepository.existsByProductIdAndStatus(
        request.productId(), ClearanceItemStatus.OPEN)) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_OPEN_EXISTS);
    }

    if (request.salePrice().compareTo(product.getRegularPrice()) >= 0) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_SALE_PRICE_NOT_DISCOUNTED);
    }

    LocalDate today = LocalDate.now(KST);
    if (!request.pickupEndAt().toLocalDate().equals(today)
        || !request.pickupStartAt().isBefore(request.pickupEndAt())) {
      throw new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_INVALID_PICKUP_WINDOW);
    }

    ClearanceItem item =
        ClearanceItem.builder()
            .store(store)
            .product(product)
            .name(product.getName())
            .regularPrice(product.getRegularPrice())
            .salePrice(request.salePrice())
            .totalQuantity(request.totalQuantity())
            .pickupStartAt(request.pickupStartAt())
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
    return clearanceItemMapper.toResponse(item);
  }

  public PageResponse<ClearanceItemResponse> getMyClearanceItems(
      Long sellerId, Long storeId, Pageable pageable) {
    verifyStoreOwnership(sellerId, storeId);
    Page<ClearanceItem> page = clearanceItemRepository.findByStoreId(storeId, pageable);
    return PageResponse.of(page.map(clearanceItemMapper::toResponse));
  }

  public ClearanceItemResponse getMyClearanceItem(
      Long sellerId, Long storeId, Long clearanceItemId) {
    verifyStoreOwnership(sellerId, storeId);
    ClearanceItem item =
        clearanceItemRepository
            .findByIdAndStoreId(clearanceItemId, storeId)
            .orElseThrow(
                () -> new BusinessException(ClearanceItemErrorCode.CLEARANCE_ITEM_NOT_FOUND));
    return clearanceItemMapper.toResponse(item);
  }

  private void verifyStoreOwnership(Long sellerId, Long storeId) {
    storeRepository
        .findByIdAndSellerId(storeId, sellerId)
        .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED));
  }
}
