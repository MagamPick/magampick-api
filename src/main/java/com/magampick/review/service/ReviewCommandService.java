package com.magampick.review.service;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.notification.domain.NotificationCategory;
import com.magampick.notification.service.NotificationService;
import com.magampick.order.domain.Order;
import com.magampick.order.repository.OrderRepository;
import com.magampick.review.domain.Review;
import com.magampick.review.domain.ReviewImage;
import com.magampick.review.domain.ReviewReply;
import com.magampick.review.dto.CreateReviewRequest;
import com.magampick.review.dto.MyReviewResponse;
import com.magampick.review.dto.ReviewReplyRequest;
import com.magampick.review.dto.StoreReviewResponse;
import com.magampick.review.dto.UpdateReviewRequest;
import com.magampick.review.exception.ReviewErrorCode;
import com.magampick.review.mapper.ReviewMapper;
import com.magampick.review.repository.ReviewReplyRepository;
import com.magampick.review.repository.ReviewRepository;
import com.magampick.seller.domain.Seller;
import com.magampick.seller.repository.SellerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 리뷰 write (작성·수정·삭제·답글) 서비스. */
@Service
@RequiredArgsConstructor
public class ReviewCommandService {

  private final ReviewRepository reviewRepository;
  private final ReviewReplyRepository reviewReplyRepository;
  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final SellerRepository sellerRepository;
  private final ReviewMapper reviewMapper;
  private final NotificationService notificationService;

  /** 리뷰 작성. */
  @Transactional
  public MyReviewResponse createReview(Long customerId, Long orderId, CreateReviewRequest request) {
    // 주문 조회
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // COMPLETED 검증
    if (!order.isCompleted()) {
      throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
    }

    // 중복 검증
    if (reviewRepository.findByOrderId(orderId).isPresent()) {
      throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
    }

    // 소비자 로드
    Customer customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // 리뷰 생성
    Review review =
        Review.builder()
            .customer(customer)
            .order(order)
            .store(order.getStore())
            .rating(request.rating())
            .content(request.content())
            .build();

    // 태그 설정
    if (request.tags() != null) {
      review.update(request.rating(), request.content(), request.tags());
    }

    // 이미지 추가
    List<String> photos = request.photos();
    if (photos != null) {
      for (int i = 0; i < photos.size(); i++) {
        review.addImage(
            ReviewImage.builder().review(review).url(photos.get(i)).sortOrder(i).build());
      }
    }

    // 저장 및 알림 발송
    Review saved = reviewRepository.save(review);
    notificationService.notifySeller(
        order.getStore().getSeller().getId(),
        "newReview",
        NotificationCategory.REVIEW,
        "새 리뷰가 등록되었어요",
        customer.getNickname() + "님이 별점 " + request.rating() + "점 리뷰를 남겼어요.",
        "/reviews");
    return reviewMapper.toMyReviewResponse(saved);
  }

  /** 리뷰 수정. */
  @Transactional
  public MyReviewResponse updateReview(
      Long customerId, Long reviewId, UpdateReviewRequest request) {
    // 리뷰 조회
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // 본인 검증
    if (!review.isOwnedBy(customerId)) {
      throw new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN);
    }

    // 답글 잠금 검증
    if (review.hasReply()) {
      throw new BusinessException(ReviewErrorCode.REVIEW_LOCKED);
    }

    // 내용 수정
    review.update(request.rating(), request.content(), request.tags());

    // 이미지 재설정
    review.clearImages();
    List<String> photos = request.photos();
    if (photos != null) {
      for (int i = 0; i < photos.size(); i++) {
        review.addImage(
            ReviewImage.builder().review(review).url(photos.get(i)).sortOrder(i).build());
      }
    }

    return reviewMapper.toMyReviewResponse(review);
  }

  /** 리뷰 삭제 (soft). */
  @Transactional
  public void deleteReview(Long customerId, Long reviewId) {
    // 리뷰 조회
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // 본인 검증
    if (!review.isOwnedBy(customerId)) {
      throw new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN);
    }

    // 답글 잠금 검증
    if (review.hasReply()) {
      throw new BusinessException(ReviewErrorCode.REVIEW_LOCKED);
    }

    review.delete();
  }

  /** 사장 답글 작성. */
  @Transactional
  public StoreReviewResponse replyToReview(
      Long sellerId, Long reviewId, ReviewReplyRequest request) {
    // 셀러 조회
    Seller seller =
        sellerRepository
            .findById(sellerId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // 리뷰 조회
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // 본인 매장 리뷰 검증
    if (!review.getStore().isOwnedBy(sellerId)) {
      throw new BusinessException(ReviewErrorCode.REPLY_STORE_FORBIDDEN);
    }

    // 답글 중복 검증
    if (reviewReplyRepository.existsByReviewId(reviewId)) {
      throw new BusinessException(ReviewErrorCode.REPLY_ALREADY_EXISTS);
    }

    // 답글 생성
    ReviewReply reply =
        ReviewReply.builder().review(review).seller(seller).content(request.content()).build();
    reviewReplyRepository.save(reply);

    // 알림 발송
    notificationService.notifyCustomer(
        review.getCustomer().getId(),
        "reviewReply",
        NotificationCategory.REVIEW,
        "사장님이 리뷰에 답글을 남겼어요",
        request.content().substring(0, Math.min(50, request.content().length())),
        "/reviews/my");

    return reviewMapper.toResponse(review);
  }
}
