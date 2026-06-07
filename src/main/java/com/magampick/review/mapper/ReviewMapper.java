package com.magampick.review.mapper;

import com.magampick.order.domain.ItemKind;
import com.magampick.order.domain.OrderItem;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewImage;
import com.magampick.review.domain.ReviewTag;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.StoreReviewResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

  @Mapping(target = "authorNickname", source = "customer.nickname")
  @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toKst")
  @Mapping(target = "products", source = "order.orderItems", qualifiedByName = "toReviewedProducts")
  @Mapping(target = "photos", source = "reviewImages", qualifiedByName = "toPhotoUrls")
  @Mapping(target = "tags", expression = "java(mapTagLabels(review.getTags()))")
  @Mapping(
      target = "ownerReply",
      expression =
          "java(review.getReviewReply() != null ? review.getReviewReply().getContent() : null)")
  StoreReviewResponse toResponse(Review review);

  @Named("toKst")
  default OffsetDateTime toKst(LocalDateTime ldt) {
    return ldt == null ? null : ldt.atOffset(ZoneOffset.ofHours(9));
  }

  @Named("toReviewedProducts")
  default List<StoreReviewResponse.ReviewedProduct> toReviewedProducts(List<OrderItem> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    return items.stream().map(this::toStoreReviewedProduct).toList();
  }

  private StoreReviewResponse.ReviewedProduct toStoreReviewedProduct(OrderItem oi) {
    Long productId =
        oi.getItemKind() == ItemKind.DEAL ? oi.getClearanceItem().getId() : oi.getProduct().getId();
    String kind = oi.getItemKind() == ItemKind.DEAL ? "deal" : "menu";
    return new StoreReviewResponse.ReviewedProduct(productId, kind, oi.getName());
  }

  /** Review → MyReviewResponse 변환. */
  default MyReviewResponse toMyReviewResponse(Review review) {
    List<MyReviewResponse.ReviewedProduct> items =
        review.getOrder().getOrderItems().stream()
            .map(
                oi -> {
                  Long productId =
                      oi.getItemKind() == ItemKind.DEAL
                          ? oi.getClearanceItem().getId()
                          : oi.getProduct().getId();
                  String kind = oi.getItemKind() == ItemKind.DEAL ? "deal" : "menu";
                  return new MyReviewResponse.ReviewedProduct(productId, kind, oi.getName());
                })
            .toList();

    List<String> photos =
        review.getReviewImages().stream()
            .sorted(Comparator.comparingInt(ReviewImage::getSortOrder))
            .map(ReviewImage::getUrl)
            .toList();

    List<String> tags = mapTagLabels(review.getTags());

    String ownerReply =
        review.getReviewReply() != null ? review.getReviewReply().getContent() : null;

    return new MyReviewResponse(
        review.getId(),
        review.getStore().getId(),
        review.getStore().getName(),
        items,
        review.getRating(),
        review.getContent(),
        tags,
        photos,
        toKst(review.getCreatedAt()),
        ownerReply);
  }

  @Named("toPhotoUrls")
  default List<String> toPhotoUrls(List<ReviewImage> images) {
    if (images == null || images.isEmpty()) {
      return List.of();
    }
    return images.stream()
        .sorted(Comparator.comparingInt(ReviewImage::getSortOrder))
        .map(ReviewImage::getUrl)
        .toList();
  }

  default List<String> mapTagLabels(Set<ReviewTag> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    return tags.stream().map(ReviewTag::getLabel).toList();
  }
}
