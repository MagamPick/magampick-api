package com.magampick.store.repository;

import com.magampick.store.domain.StoreBusinessHour;
import java.time.DayOfWeek;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreBusinessHourRepository extends JpaRepository<StoreBusinessHour, Long> {

  Optional<StoreBusinessHour> findByStoreIdAndDayOfWeek(Long storeId, DayOfWeek dayOfWeek);
}
