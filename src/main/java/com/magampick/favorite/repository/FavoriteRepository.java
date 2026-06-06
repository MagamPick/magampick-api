package com.magampick.favorite.repository;

import com.magampick.favorite.domain.Favorite;
import java.util.Collection;
import java.util.List;
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

  /**
   * 소비자 단골 배치 조회 — N+1 방지. 후보 storeIds 중 customerId 의 즐겨찾기인 store_id 목록 반환.
   *
   * @param customerId 소비자 ID
   * @param storeIds 검사 대상 매장 ID 목록
   */
  @Query("SELECT f.store.id FROM Favorite f" + " WHERE f.customer.id = :cid AND f.store.id IN :ids")
  List<Long> findStoreIdsByCustomerIdAndStoreIdIn(
      @Param("cid") Long customerId, @Param("ids") Collection<Long> storeIds);
}
