package com.magampick.favorite.service;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.dto.FavoriteStoreResponse;
import com.magampick.favorite.mapper.FavoriteMapper;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.response.PageResponse;
import com.magampick.store.domain.Store;
import com.magampick.store.exception.StoreErrorCode;
import com.magampick.store.repository.StoreRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final StoreRepository storeRepository;
  private final CustomerRepository customerRepository;
  private final FavoriteMapper favoriteMapper;

  @Transactional
  public FavoriteAddResponse addFavorite(Long customerId, Long storeId) {
    Store store =
        storeRepository
            .findById(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

    Optional<Favorite> existing =
        favoriteRepository.findByCustomerIdAndStoreId(customerId, storeId);
    if (existing.isPresent()) {
      return favoriteMapper.toAddResponse(existing.get());
    }

    Customer customer = customerRepository.getReferenceById(customerId);
    Favorite favorite = Favorite.builder().customer(customer).store(store).build();
    favoriteRepository.save(favorite);
    log.info("즐겨찾기 등록됨. customerId={}, storeId={}", customerId, storeId);
    return favoriteMapper.toAddResponse(favorite);
  }

  @Transactional
  public void removeFavorite(Long customerId, Long storeId) {
    favoriteRepository.deleteByCustomerIdAndStoreId(customerId, storeId);
    log.info("즐겨찾기 해제됨. customerId={}, storeId={}", customerId, storeId);
  }

  public PageResponse<FavoriteStoreResponse> getFavorites(Long customerId, Pageable pageable) {
    Page<Favorite> page = favoriteRepository.findByCustomerIdWithStore(customerId, pageable);
    return PageResponse.of(page.map(favoriteMapper::toStoreResponse));
  }
}
