package com.magampick.store.repository;

import com.magampick.store.domain.StoreBusinessHour;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreBusinessHourRepository extends JpaRepository<StoreBusinessHour, Long> {

  Optional<StoreBusinessHour> findByStoreIdAndDayOfWeek(Long storeId, DayOfWeek dayOfWeek);

  List<StoreBusinessHour> findByStoreId(Long storeId);

  /**
   * 매장의 영업시간 row 전체 삭제. 전체 교체 저장 (delete + saveAll) 의 delete 단계로 사용. flushAutomatically /
   * clearAutomatically 로 같은 트랜잭션 내 후행 INSERT 가 UNIQUE(`store_id`, `day_of_week`) 와 충돌하지 않게 한다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from StoreBusinessHour h where h.store.id = :storeId")
  void deleteByStoreId(@Param("storeId") Long storeId);
}
