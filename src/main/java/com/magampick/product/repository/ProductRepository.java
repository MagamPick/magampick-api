package com.magampick.product.repository;

import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import java.util.List;
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

  /**
   * 소비자 메뉴 탭 — ON_SALE 상품만 (소프트 삭제 제외). flat 리스트, FE 가 category 로 그룹화.
   *
   * @param storeId 매장 ID
   * @param status {@link ProductStatus#ON_SALE}
   * @return 판매중 상품 목록
   */
  List<Product> findByStoreIdAndStatusAndDeletedAtIsNull(Long storeId, ProductStatus status);
}
