package com.magampick.store.repository;

import com.magampick.store.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

  List<Store> findBySellerId(Long sellerId);

  Optional<Store> findByIdAndSellerId(Long storeId, Long sellerId);
}
