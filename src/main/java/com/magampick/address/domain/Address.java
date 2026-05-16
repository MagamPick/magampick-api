package com.magampick.address.domain;

import com.magampick.customer.domain.Customer;
import com.magampick.global.common.BaseEntity;
import com.magampick.global.common.GeometryUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

/** 소비자 주소지. customer 1:N 종속. 최대 3개 (앱 레벨 제약). */
@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Column(name = "label", nullable = false, length = 20)
  private String label;

  @Column(name = "road_address", nullable = false, length = 200)
  private String roadAddress;

  @Column(name = "jibun_address", length = 200)
  private String jibunAddress;

  @Column(name = "detail_address", length = 100)
  private String detailAddress;

  @Column(name = "zonecode", length = 10)
  private String zonecode;

  @Column(name = "location", nullable = false, columnDefinition = "GEOGRAPHY(POINT, 4326)")
  private Point location;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Builder
  private Address(
      Customer customer,
      String label,
      String roadAddress,
      String jibunAddress,
      String detailAddress,
      String zonecode,
      Point location,
      boolean isDefault) {
    this.customer = customer;
    this.label = label;
    this.roadAddress = roadAddress;
    this.jibunAddress = jibunAddress;
    this.detailAddress = detailAddress;
    this.zonecode = zonecode;
    this.location = location;
    this.isDefault = isDefault;
  }

  public void changeLabel(String newLabel) {
    this.label = newLabel;
  }

  public void changeRoadAddress(String newRoadAddress) {
    this.roadAddress = newRoadAddress;
  }

  public void changeJibunAddress(String newJibunAddress) {
    this.jibunAddress = newJibunAddress;
  }

  public void changeDetailAddress(String newDetailAddress) {
    this.detailAddress = newDetailAddress;
  }

  public void changeZonecode(String newZonecode) {
    this.zonecode = newZonecode;
  }

  public void changeLocation(double latitude, double longitude) {
    this.location = GeometryUtil.toPoint(latitude, longitude);
  }

  public void markAsDefault() {
    this.isDefault = true;
  }

  public void unmarkAsDefault() {
    this.isDefault = false;
  }

  public boolean isOwnedBy(Long customerId) {
    return this.customer != null && customerId != null && customerId.equals(this.customer.getId());
  }
}
