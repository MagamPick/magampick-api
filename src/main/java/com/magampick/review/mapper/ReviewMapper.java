package com.magampick.review.mapper;

import com.magampick.order.domain.OrderItem;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewImage;
import com.magampick.review.domain.ReviewTag;
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
    return items.stream()
        .map(
            oi ->
                new StoreReviewResponse.ReviewedProduct(
                    oi.getClearanceItem().getId(), "deal", oi.getClearanceItem().getName()))
        .toList();
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
