package com.magampick.seller.repository;

import com.magampick.seller.domain.Seller;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {

  boolean existsByEmail(String email);

  Optional<Seller> findByEmail(String email);
}
