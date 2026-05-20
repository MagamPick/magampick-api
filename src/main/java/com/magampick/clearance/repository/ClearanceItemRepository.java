package com.magampick.clearance.repository;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClearanceItemRepository extends JpaRepository<ClearanceItem, Long> {

  boolean existsByProductIdAndStatus(Long productId, ClearanceItemStatus status);

  @EntityGraph(attributePaths = "product")
  Page<ClearanceItem> findByStoreId(Long storeId, Pageable pageable);

  Optional<ClearanceItem> findByIdAndStoreId(Long id, Long storeId);

  @Modifying
  @Query(
      "UPDATE ClearanceItem c SET c.status = com.magampick.clearance.domain.ClearanceItemStatus.CLOSED"
          + " WHERE c.status = com.magampick.clearance.domain.ClearanceItemStatus.OPEN AND c.pickupEndAt < :now")
  int closeExpiredItems(@Param("now") LocalDateTime now);
}
