package com.magampick.favorite.service;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.favorite.domain.Favorite;
import com.magampick.favorite.dto.FavoriteAddResponse;
import com.magampick.favorite.mapper.FavoriteMapper;
import com.magampick.favorite.repository.FavoriteRepository;
import com.magampick.store.domain.Store;
import com.magampick.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단골 INSERT 를 독립 트랜잭션(REQUIRES_NEW)으로 실행하는 헬퍼.
 *
 * <p>동시 추가 레이스로 unique 제약(uk_favorites_customer_store)을 위반하면 JPA 규약상 현재 영속성 컨텍스트의 트랜잭션이
 * rollback-only 로 마킹된다. 바깥 트랜잭션에서 직접 saveAndFlush 하면 위반 예외를 잡아도 커밋 시점에 {@code
 * UnexpectedRollbackException} 으로 500 이 된다. INSERT 만 별도 트랜잭션으로 떼어내 위반 시 그 트랜잭션만 롤백시키고, 바깥 트랜잭션은
 * 깨끗하게 유지해 호출 측이 멱등 재조회로 성공 응답을 만들 수 있게 한다.
 *
 * <p>호출 전 매장 존재는 검증된 상태이므로 {@link StoreRepository#getReferenceById}/{@link
 * CustomerRepository#getReferenceById} 프록시로 FK 만 매핑한다(불필요한 SELECT 회피).
 */
@Component
@RequiredArgsConstructor
public class FavoriteInserter {

  private final FavoriteRepository favoriteRepository;
  private final CustomerRepository customerRepository;
  private final StoreRepository storeRepository;
  private final FavoriteMapper favoriteMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public FavoriteAddResponse insert(Long customerId, Long storeId) {
    Customer customerRef = customerRepository.getReferenceById(customerId);
    Store storeRef = storeRepository.getReferenceById(storeId);
    Favorite favorite = Favorite.builder().customer(customerRef).store(storeRef).build();
    Favorite saved = favoriteRepository.saveAndFlush(favorite);
    return favoriteMapper.toAddResponse(saved);
  }
}
