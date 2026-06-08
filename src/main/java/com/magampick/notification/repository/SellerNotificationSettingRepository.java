package com.magampick.notification.repository;

import com.magampick.notification.domain.SellerNotificationSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerNotificationSettingRepository
    extends JpaRepository<SellerNotificationSetting, Long> {

  Optional<SellerNotificationSetting> findBySellerId(Long sellerId);
}
