package com.magampick.store.repository;

import com.magampick.store.domain.Store;
import com.magampick.store.domain.StoreStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

  @Query("SELECT s FROM Store s JOIN FETCH s.categories WHERE s.seller.id = :sellerId")
  List<Store> findBySellerId(@Param("sellerId") Long sellerId);

  @Query(
      "SELECT s FROM Store s JOIN FETCH s.categories"
          + " WHERE s.id = :storeId AND s.seller.id = :sellerId")
  Optional<Store> findByIdAndSellerId(
      @Param("storeId") Long storeId, @Param("sellerId") Long sellerId);

  @Query(
      value =
          "SELECT s FROM Store s JOIN FETCH s.seller"
              + " WHERE (:status IS NULL OR s.status = :status)",
      countQuery = "SELECT COUNT(s) FROM Store s WHERE (:status IS NULL OR s.status = :status)")
  Page<Store> findByStatusFilter(@Param("status") StoreStatus status, Pageable pageable);

  @Query("SELECT s FROM Store s JOIN FETCH s.categories WHERE s.id = :storeId")
  Optional<Store> findByIdWithCategories(@Param("storeId") Long storeId);
}
