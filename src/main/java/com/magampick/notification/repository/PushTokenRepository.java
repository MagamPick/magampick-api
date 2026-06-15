package com.magampick.notification.repository;

import com.magampick.global.security.Role;
import com.magampick.notification.domain.PushToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

  Optional<PushToken> findByToken(String token);

  List<PushToken> findByOwnerTypeAndOwnerId(Role ownerType, Long ownerId);

  void deleteByToken(String token);

  void deleteByTokenIn(Collection<String> tokens);
}
