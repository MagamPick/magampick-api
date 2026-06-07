package com.magampick.store.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.seller.domain.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_id", nullable = false)
  private Seller seller;

  @Column(name = "business_number", nullable = false, length = 10)
  private String businessNumber;

  @Column(name = "representative_name", nullable = false, length = 30)
  private String representativeName;

  @Column(name = "open_date", nullable = false)
  private LocalDate openDate;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Column(name = "road_address", nullable = false, length = 200)
  private String roadAddress;

  @Column(name = "jibun_address", length = 200)
  private String jibunAddress;

  @Column(name = "detail_address", length = 100)
  private String detailAddress;

  @Column(name = "zonecode", nullable = false, length = 10)
  private String zonecode;

  @Column(name = "location", nullable = false, columnDefinition = "geography(Point,4326)")
  private Point location;

  @Column(name = "phone", nullable = false, length = 20)
  private String phone;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_status", nullable = false, length = 15)
  private OperationStatus operationStatus;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Builder
  private Store(
      Seller seller,
      String businessNumber,
      String representativeName,
      LocalDate openDate,
      String name,
      String roadAddress,
      String jibunAddress,
      String detailAddress,
      String zonecode,
      Point location,
      String phone,
      String description,
      String imageUrl,
      OperationStatus operationStatus) {
    this.seller = seller;
    this.businessNumber = businessNumber;
    this.representativeName = representativeName;
    this.openDate = openDate;
    this.name = name;
    this.roadAddress = roadAddress;
    this.jibunAddress = jibunAddress;
    this.detailAddress = detailAddress;
    this.zonecode = zonecode;
    this.location = location;
    this.phone = phone;
    this.description = description;
    this.imageUrl = imageUrl;
    this.operationStatus = operationStatus;
  }

  public boolean isOwnedBy(Long sellerId) {
    return seller.getId().equals(sellerId);
  }

  /** 영업 상태 변경. 전이 가능 여부 검증은 호출 측(Service) 책임. */
  public void changeOperationStatus(OperationStatus to) {
    this.operationStatus = to;
  }

  public void changeName(String name) {
    this.name = name;
  }

  public void changePhone(String phone) {
    this.phone = phone;
  }

  public void changeDescription(String description) {
    this.description = description;
  }

  public void changeDetailAddress(String detailAddress) {
    this.detailAddress = detailAddress;
  }

  /** 주소 변경 — 자체 DB 지오코딩 결과까지 한 묶음. 4필드 atomic update. */
  public void changeAddress(
      String roadAddress, String jibunAddress, String zonecode, Point location) {
    this.roadAddress = roadAddress;
    this.jibunAddress = jibunAddress;
    this.zonecode = zonecode;
    this.location = location;
  }

  public void changeImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }
}
