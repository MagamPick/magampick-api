package com.magampick.clearance.repository;

import com.magampick.clearance.domain.ClearanceItem;
import com.magampick.clearance.domain.ClearanceItemStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClearanceItemRepository extends JpaRepository<ClearanceItem, Long> {

  boolean existsByProductIdAndStatus(Long productId, ClearanceItemStatus status);

  @EntityGraph(attributePaths = "product")
  Page<ClearanceItem> findByStoreId(Long storeId, Pageable pageable);

  Optional<ClearanceItem> findByIdAndStoreId(Long id, Long storeId);
}
