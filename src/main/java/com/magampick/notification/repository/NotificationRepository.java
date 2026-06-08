package com.magampick.notification.repository;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.Notification;
import com.magampick.notification.domain.NotificationCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  List<Notification> findByReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
      Role receiverType, Long receiverId);

  List<Notification> findByReceiverTypeAndReceiverIdAndCategoryOrderByCreatedAtDesc(
      Role receiverType, Long receiverId, NotificationCategory category);

  long countByReceiverTypeAndReceiverIdAndIsReadFalse(Role receiverType, Long receiverId);

  Optional<Notification> findByIdAndReceiverTypeAndReceiverId(
      Long id, Role receiverType, Long receiverId);

  @Modifying
  @Query(
      "UPDATE Notification n SET n.isRead = true"
          + " WHERE n.receiverType = :type AND n.receiverId = :id AND n.isRead = false")
  void markAllRead(@Param("type") Role type, @Param("id") Long id);
}
