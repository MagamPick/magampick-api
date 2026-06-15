package com.magampick.notification.repository;

import com.magampick.notification.domain.CustomerNotificationSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerNotificationSettingRepository
    extends JpaRepository<CustomerNotificationSetting, Long> {

  Optional<CustomerNotificationSetting> findByCustomerId(Long customerId);
}
