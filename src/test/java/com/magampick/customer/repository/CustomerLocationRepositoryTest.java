package com.magampick.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.magampick.TestcontainersConfiguration;
import com.magampick.customer.domain.Customer;
import com.magampick.customer.domain.CustomerLocation;
import com.magampick.global.common.GeometryUtil;
import com.magampick.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class CustomerLocationRepositoryTest {

  @Autowired CustomerLocationRepository customerLocationRepository;
  @Autowired CustomerRepository customerRepository;

  // 기준점: 서울 시청 인근
  private static final double STORE_LAT = 37.5665;
  private static final double STORE_LNG = 126.9780;
  private static final double DISTANCE_METERS = 3000.0;

  private Customer saveCustomer() {
    return customerRepository.save(
        Customer.builder()
            .email("test_" + System.nanoTime() + "@test.com")
            .passwordHash("hash")
            .nickname("테스트유저")
            .build());
  }

  private CustomerLocation locationOf(
      Long customerId, double lat, double lng, LocalDateTime updatedAt) {
    return CustomerLocation.of(customerId, GeometryUtil.toPoint(lat, lng), updatedAt);
  }

  @Test
  void 반경_이내_신선한_소비자_포함() {
    // given — 매장 바로 옆 (~100m), 30분 전 위치 갱신
    Customer customer = saveCustomer();
    LocalDateTime fresh = LocalDateTime.now().minusMinutes(30);
    customerLocationRepository.save(
        locationOf(customer.getId(), STORE_LAT + 0.0005, STORE_LNG + 0.0005, fresh));

    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    // when
    List<Long> result =
        customerLocationRepository.findCustomerIdsNear(
            STORE_LAT, STORE_LNG, DISTANCE_METERS, threshold);

    // then
    assertThat(result).contains(customer.getId());
  }

  @Test
  void 반경_밖_소비자_제외() {
    // given — 노원구 (시청에서 약 12km)
    Customer customer = saveCustomer();
    LocalDateTime fresh = LocalDateTime.now().minusMinutes(30);
    customerLocationRepository.save(locationOf(customer.getId(), 37.6548, 127.0649, fresh));

    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    // when
    List<Long> result =
        customerLocationRepository.findCustomerIdsNear(
            STORE_LAT, STORE_LNG, DISTANCE_METERS, threshold);

    // then
    assertThat(result).doesNotContain(customer.getId());
  }

  @Test
  void 신선도_1시간_경계_이전_소비자_제외() {
    // given — 반경 이내지만 2시간 전 위치 갱신 (신선도 초과)
    Customer customer = saveCustomer();
    LocalDateTime stale = LocalDateTime.now().minusHours(2);
    customerLocationRepository.save(
        locationOf(customer.getId(), STORE_LAT + 0.0005, STORE_LNG + 0.0005, stale));

    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    // when
    List<Long> result =
        customerLocationRepository.findCustomerIdsNear(
            STORE_LAT, STORE_LNG, DISTANCE_METERS, threshold);

    // then
    assertThat(result).doesNotContain(customer.getId());
  }

  @Test
  void 여러_소비자_중_조건_맞는_소비자만_반환() {
    // given
    Customer nearbyFresh = saveCustomer(); // 반경 이내 + 신선 → 포함
    Customer farFresh = saveCustomer(); // 반경 밖 + 신선 → 제외
    Customer nearbyStale = saveCustomer(); // 반경 이내 + 신선도 초과 → 제외

    LocalDateTime fresh = LocalDateTime.now().minusMinutes(30);
    LocalDateTime stale = LocalDateTime.now().minusHours(2);

    customerLocationRepository.save(
        locationOf(nearbyFresh.getId(), STORE_LAT + 0.0005, STORE_LNG + 0.0005, fresh));
    customerLocationRepository.save(locationOf(farFresh.getId(), 37.6548, 127.0649, fresh));
    customerLocationRepository.save(
        locationOf(nearbyStale.getId(), STORE_LAT + 0.0005, STORE_LNG + 0.0005, stale));

    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    // when
    List<Long> result =
        customerLocationRepository.findCustomerIdsNear(
            STORE_LAT, STORE_LNG, DISTANCE_METERS, threshold);

    // then
    assertThat(result)
        .containsExactlyInAnyOrder(nearbyFresh.getId())
        .doesNotContain(farFresh.getId(), nearbyStale.getId());
  }
}
