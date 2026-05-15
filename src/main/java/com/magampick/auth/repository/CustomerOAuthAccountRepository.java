package com.magampick.auth.repository;

import com.magampick.auth.domain.CustomerOAuthAccount;
import com.magampick.auth.domain.OAuthProviderType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOAuthAccountRepository extends JpaRepository<CustomerOAuthAccount, Long> {

  Optional<CustomerOAuthAccount> findByProviderAndProviderUserId(
      OAuthProviderType provider, String providerUserId);
}
