package com.magampick.product.repository;

import com.magampick.product.domain.Product;
import com.magampick.product.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  /**
   * 소비자 상세 조회용 — store 함께 fetch, 소프트 삭제 제외. N+1 방지.
   *
   * @param id 상품 ID
   * @return store 초기화된 Product
   */
  @EntityGraph(attributePaths = "store")
  Optional<Product> findByIdAndDeletedAtIsNull(Long id);

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

  /**
   * Phase 9 검색: 지정 매장 ID 내 ON_SALE 상품 이름 ILIKE 부분일치 검색 (소프트 삭제 제외). id, storeId, name, imageUrl,
   * regularPrice 반환.
   *
   * @param storeIds 후보 매장 ID 목록
   * @param q 검색 키워드
   * @return {@link ProductSearchCandidate} projection 목록
   */
  @Query(
      value =
          """
          SELECT p.id          AS id,
                 p.store_id    AS storeId,
                 p.name        AS name,
                 p.image_url   AS imageUrl,
                 p.regular_price AS regularPrice
          FROM products p
          WHERE p.status = 'ON_SALE'
            AND p.deleted_at IS NULL
            AND p.store_id IN :storeIds
            AND p.name ILIKE '%' || :q || '%' ESCAPE '\\'
          """,
      nativeQuery = true)
  List<ProductSearchCandidate> searchOnSaleProductsByStoreIds(
      @Param("storeIds") Collection<Long> storeIds, @Param("q") String q);

  /**
   * Phase 9 자동완성: 지정 매장 ID 내 ON_SALE 상품 이름 word_similarity 제안 (소프트 삭제 제외). 유사도 내림차순 정렬.
   *
   * @param storeIds 후보 매장 ID 목록
   * @param q 자동완성 입력어
   * @param threshold word_similarity 임계값
   * @return {@link ProductNameSuggestion} projection 목록 (유사도 내림차순)
   */
  @Query(
      value =
          """
          SELECT p.name AS name,
                 word_similarity(:q, p.name) AS similarity
          FROM products p
          WHERE p.status = 'ON_SALE'
            AND p.deleted_at IS NULL
            AND p.store_id IN :storeIds
            AND word_similarity(:q, p.name) >= :threshold
          ORDER BY word_similarity(:q, p.name) DESC
          """,
      nativeQuery = true)
  List<ProductNameSuggestion> suggestProductNamesByStoreIds(
      @Param("storeIds") Collection<Long> storeIds,
      @Param("q") String q,
      @Param("threshold") double threshold);
}
