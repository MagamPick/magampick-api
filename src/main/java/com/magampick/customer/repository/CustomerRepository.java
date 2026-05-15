package com.magampick.customer.repository;

import com.magampick.customer.domain.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  boolean existsByEmail(String email);

  Optional<Customer> findByEmail(String email);
}
