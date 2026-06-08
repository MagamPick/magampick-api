package com.magampick.favorite.repository;

import com.magampick.favorite.domain.Favorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

  Optional<Favorite> findByCustomerIdAndStoreId(Long customerId, Long storeId);

  void deleteByCustomerIdAndStoreId(Long customerId, Long storeId);

  /**
   * 단골 매장 목록 + PostGIS 거리 조회. 거리/영업상태 필터 없음(전체 단골), 소프트삭제 매장만 제외.
   *
   * @param customerId 소비자 ID
   * @param lat origin 위도 (기본 주소지)
   * @param lng origin 경도 (기본 주소지)
   * @return {@link FavoriteStoreCandidate} projection 목록
   */
  @Query(
      value =
          """
          SELECT s.id        AS storeId,
                 s.name      AS name,
                 s.image_url AS imageUrl,
                 ST_Distance(s.location,
                             ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distanceMeters,
                 f.created_at AS createdAt
          FROM favorites f
          JOIN stores s ON s.id = f.store_id
          WHERE f.customer_id = :cid
            AND s.deleted_at IS NULL
          """,
      nativeQuery = true)
  List<FavoriteStoreCandidate> findFavoriteStoresWithDistance(
      @Param("cid") Long customerId, @Param("lat") double lat, @Param("lng") double lng);

  /**
   * 특정 매장을 즐겨찾기한 소비자 ID 목록 반환. 떨이 등록·임박 알림 발송 대상 계산용.
   *
   * @param storeId 매장 ID
   * @return 즐겨찾기한 소비자 ID 목록
   */
  @Query("SELECT f.customer.id FROM Favorite f WHERE f.store.id = :storeId")
  List<Long> findCustomerIdsByStoreId(@Param("storeId") Long storeId);

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
