package com.magampick.address.repository;

import com.magampick.address.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

  long countByCustomerId(Long customerId);

  List<Address> findByCustomerIdOrderByIsDefaultDescCreatedAtAscIdAsc(Long customerId);

  Optional<Address> findByCustomerIdAndIsDefaultTrue(Long customerId);

  Optional<Address> findFirstByCustomerIdAndIdNotOrderByCreatedAtAscIdAsc(
      Long customerId, Long excludeId);
}
