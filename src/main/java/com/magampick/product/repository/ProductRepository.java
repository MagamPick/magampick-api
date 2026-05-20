package com.magampick.product.repository;

import com.magampick.product.domain.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

  boolean existsByStoreIdAndNameAndDeletedAtIsNull(Long storeId, String name);

  boolean existsByStoreIdAndNameAndDeletedAtIsNullAndIdNot(
      Long storeId, String name, Long excludeId);

  Page<Product> findByStoreIdAndDeletedAtIsNull(Long storeId, Pageable pageable);

  Optional<Product> findByIdAndStoreIdAndDeletedAtIsNull(Long productId, Long storeId);
}
