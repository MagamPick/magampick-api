package com.magampick.store.repository;

import com.magampick.store.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

  /** 사장 보유 매장 목록 — 등록순(`created_at` asc). 노션 "보유 매장 목록 조회" 정렬 명세 정합. */
  List<Store> findBySellerIdOrderByCreatedAtAsc(Long sellerId);

  /** 소비자 매장 단건 조회 (소프트 삭제 제외). */
  Optional<Store> findByIdAndDeletedAtIsNull(Long storeId);

  Optional<Store> findByIdAndSellerId(Long storeId, Long sellerId);

  /**
   * 매장 단건 거리 조회 (미터). 기본 주소지 origin → 매장 location 간 ST_Distance (geography).
   *
   * @param storeId 매장 ID
   * @param lat origin 위도
   * @param lng origin 경도
   * @return 거리(미터), 매장 없으면 null
   */
  @Query(
      value =
          "SELECT ST_Distance(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)"
              + " FROM stores s WHERE s.id = :storeId",
      nativeQuery = true)
  Double findDistanceMeters(
      @Param("storeId") Long storeId, @Param("lat") double lat, @Param("lng") double lng);

  /**
   * 소비자 매장 목록용 PostGIS 후보 쿼리. 조건: deleted_at IS NULL, operation_status=OPEN, 5km 이내 (ST_DWithin
   * geography), 오늘 영업요일 존재(store_business_hours). ST_Distance 로 거리(m) 계산.
   *
   * <p>origin 은 lat/lng double 로 받아 SQL 에서 포인트 생성 — JTS 바인딩 회피.
   *
   * @param lat origin 위도
   * @param lng origin 경도
   * @param today DayOfWeek.name() 예) "SATURDAY"
   */
  @Query(
      value =
          """
          SELECT s.id AS id, s.name AS name, s.image_url AS imageUrl,
                 ST_Distance(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
                     AS distanceMeters
          FROM stores s
          WHERE s.deleted_at IS NULL
            AND s.operation_status = 'OPEN'
            AND ST_DWithin(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 5000)
            AND EXISTS (
                SELECT 1 FROM store_business_hours h
                WHERE h.store_id = s.id AND h.day_of_week = :today
            )
          """,
      nativeQuery = true)
  List<StoreCandidate> findOpenStoresWithin5km(
      @Param("lat") double lat, @Param("lng") double lng, @Param("today") String today);
}
