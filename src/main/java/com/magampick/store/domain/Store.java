package com.magampick.store.domain;

import com.magampick.global.common.BaseEntity;
import com.magampick.global.exception.BusinessException;
import com.magampick.seller.domain.Seller;
import com.magampick.store.exception.StoreErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

  @Column(name = "image_url", nullable = false, length = 500)
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private StoreStatus status;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "store_store_categories",
      joinColumns = @JoinColumn(name = "store_id"),
      inverseJoinColumns = @JoinColumn(name = "store_category_id"))
  private List<StoreCategory> categories = new ArrayList<>();

  @Builder
  private Store(
      Seller seller,
      String name,
      String roadAddress,
      String jibunAddress,
      String detailAddress,
      String zonecode,
      Point location,
      String phone,
      String description,
      String imageUrl,
      StoreStatus status,
      List<StoreCategory> categories) {
    this.seller = seller;
    this.name = name;
    this.roadAddress = roadAddress;
    this.jibunAddress = jibunAddress;
    this.detailAddress = detailAddress;
    this.zonecode = zonecode;
    this.location = location;
    this.phone = phone;
    this.description = description;
    this.imageUrl = imageUrl;
    this.status = status;
    this.categories = categories != null ? new ArrayList<>(categories) : new ArrayList<>();
  }

  public boolean isOwnedBy(Long sellerId) {
    return seller.getId().equals(sellerId);
  }

  public void approve() {
    if (status != StoreStatus.PENDING) {
      throw new BusinessException(StoreErrorCode.STORE_ALREADY_REVIEWED);
    }
    this.status = StoreStatus.APPROVED;
  }

  public void reject(String reason) {
    if (status != StoreStatus.PENDING) {
      throw new BusinessException(StoreErrorCode.STORE_ALREADY_REVIEWED);
    }
    this.status = StoreStatus.REJECTED;
    this.rejectionReason = reason;
  }
}
