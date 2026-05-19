package com.magampick.product.repository;

import com.magampick.product.domain.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

  boolean existsByStoreIdAndName(Long storeId, String name);

  Page<Product> findByStoreId(Long storeId, Pageable pageable);

  Optional<Product> findByIdAndStoreId(Long productId, Long storeId);
}
