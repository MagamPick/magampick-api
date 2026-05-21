package com.magampick.favorite.repository;

import com.magampick.favorite.domain.Favorite;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

  Optional<Favorite> findByCustomerIdAndStoreId(Long customerId, Long storeId);

  void deleteByCustomerIdAndStoreId(Long customerId, Long storeId);

  @Query(
      value = "SELECT f FROM Favorite f JOIN FETCH f.store WHERE f.customer.id = :customerId",
      countQuery = "SELECT COUNT(f) FROM Favorite f WHERE f.customer.id = :customerId")
  Page<Favorite> findByCustomerIdWithStore(@Param("customerId") Long customerId, Pageable pageable);
}
