package com.magampick.store.repository;

import com.magampick.store.domain.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

  /** 사장 보유 매장 목록 — 등록순(`created_at` asc). 노션 "보유 매장 목록 조회" 정렬 명세 정합. */
  List<Store> findBySellerIdOrderByCreatedAtAsc(Long sellerId);

  Optional<Store> findByIdAndSellerId(Long storeId, Long sellerId);
}
