package com.magampick.seller.service;

import com.magampick.global.exception.BusinessException;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.dto.SellerPhoneUpdateRequest;
import com.magampick.seller.dto.SellerProfileResponse;
import com.magampick.seller.dto.SellerProfileUpdateRequest;
import com.magampick.seller.exception.SellerErrorCode;
import com.magampick.seller.mapper.SellerMapper;
import com.magampick.seller.repository.SellerRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerService {

  private static final int SELLER_NAME_MIN = 2;
  private static final int SELLER_NAME_MAX = 20;

  private final SellerRepository sellerRepository;
  private final SellerMapper sellerMapper;

  public SellerProfileResponse getProfile(Long sellerId) {
    Seller seller = findActiveSeller(sellerId);
    return sellerMapper.toProfileResponse(seller);
  }

  @Transactional
  public SellerProfileResponse updateProfile(Long sellerId, SellerProfileUpdateRequest request) {
    Seller seller = findActiveSeller(sellerId);
    validateSellerName(request.name());
    seller.changeOwnerName(request.name());
    log.info("사장 이름 변경됨. sellerId={}", sellerId);
    return sellerMapper.toProfileResponse(seller);
  }

  @Transactional
  public SellerProfileResponse updatePhone(Long sellerId, SellerPhoneUpdateRequest request) {
    Seller seller = findActiveSeller(sellerId);
    seller.changePhone(request.phone(), LocalDateTime.now());
    log.info("사장 휴대폰 변경됨. sellerId={}", sellerId);
    return sellerMapper.toProfileResponse(seller);
  }

  private Seller findActiveSeller(Long sellerId) {
    Seller seller =
        sellerRepository
            .findById(sellerId)
            .orElseThrow(() -> new BusinessException(SellerErrorCode.SELLER_NOT_FOUND));
    if (seller.isDeleted()) {
      throw new BusinessException(SellerErrorCode.SELLER_NOT_FOUND);
    }
    return seller;
  }

  private void validateSellerName(String name) {
    int length = name == null ? 0 : name.trim().length();
    if (length < SELLER_NAME_MIN || length > SELLER_NAME_MAX) {
      throw new BusinessException(SellerErrorCode.SELLER_NAME_INVALID);
    }
  }
}
