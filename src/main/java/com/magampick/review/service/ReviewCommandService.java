package com.magampick.review.service;

import com.magampick.customer.domain.Customer;
import com.magampick.customer.repository.CustomerRepository;
import com.magampick.global.exception.BusinessException;
import com.magampick.global.storage.StorageService;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 리뷰 write (작성·수정·삭제·답글) 서비스. */
@Service
@RequiredArgsConstructor
public class ReviewCommandService {

  /** 리뷰 사진 최대 장수 (keep + 신규 합). */
  private static final int MAX_PHOTOS = 3;

  private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final ReviewRepository reviewRepository;
  private final ReviewReplyRepository reviewReplyRepository;
  private final OrderRepository orderRepository;
  private final CustomerRepository customerRepository;
  private final SellerRepository sellerRepository;
  private final ReviewMapper reviewMapper;
  private final NotificationService notificationService;
  private final StorageService storageService;

  /** 리뷰 작성. 첨부 사진은 OCI 에 업로드한 뒤 그 URL 을 저장한다. */
  @Transactional
  public MyReviewResponse createReview(
      Long customerId, Long orderId, CreateReviewRequest request, List<MultipartFile> photos) {
    // 주문 조회
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

    // COMPLETED 검증
    if (!order.isCompleted()) {
      throw new BusinessException(ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
    }

    // 주문 소유권 검증 (타인 주문에 리뷰 작성 차단)
    if (!order.isOwnedBy(customerId)) {
      throw new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN);
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

    // 사진 검증 (개수·형식) — 업로드 전에 차단
    validateReviewPhotos(0, photos);

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

    // 사진 OCI 업로드 후 URL 저장
    addImages(review, uploadPhotos(photos));

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

  /**
   * 리뷰 수정. 사진은 유지할 기존 URL ({@code keepImageUrls}) 과 새로 첨부한 파일로 재구성한다 — 최종 = 유지 URL + 새 업로드 URL
   * (순서대로). {@code keepImageUrls} 중 현재 리뷰에 실재하지 않는 URL 은 무시한다.
   */
  @Transactional
  public MyReviewResponse updateReview(
      Long customerId, Long reviewId, UpdateReviewRequest request, List<MultipartFile> photos) {
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

    // 유지할 기존 사진 (현재 리뷰에 실재하는 URL 만)
    List<String> keptUrls = retainExistingUrls(request.keepImageUrls(), review);

    // 사진 검증 (유지 + 신규 합 개수·형식) — 업로드 전에 차단
    validateReviewPhotos(keptUrls.size(), photos);

    // 이미지 재구성: 유지 URL + 새 업로드 URL (순서대로)
    List<String> finalUrls = new ArrayList<>(keptUrls);
    finalUrls.addAll(uploadPhotos(photos));
    review.clearImages();
    addImages(review, finalUrls);

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

  // ── 사진 처리 (OCI 업로드) ──────────────────────────────────────────────────────

  /** keepImageUrls 중 현재 리뷰에 실재하는 URL 만 입력 순서대로 추린다 (임의 문자열 저장 차단). */
  private List<String> retainExistingUrls(List<String> keepImageUrls, Review review) {
    if (keepImageUrls == null || keepImageUrls.isEmpty()) {
      return List.of();
    }
    Set<String> existing = new HashSet<>();
    for (ReviewImage image : review.getReviewImages()) {
      existing.add(image.getUrl());
    }
    return keepImageUrls.stream().filter(existing::contains).toList();
  }

  /** 유지 사진 수 + 신규 첨부 수가 최대 장수를 넘지 않는지, 각 파일이 규격에 맞는지 검증한다. */
  private void validateReviewPhotos(int keepCount, List<MultipartFile> photos) {
    long newCount = photos == null ? 0 : photos.stream().filter(this::hasImage).count();
    if (keepCount + newCount > MAX_PHOTOS) {
      throw new BusinessException(ReviewErrorCode.REVIEW_IMAGE_TOO_MANY);
    }
    if (photos != null) {
      photos.forEach(this::validateImage);
    }
  }

  /** 첨부 파일들을 OCI 에 업로드하고 URL 목록을 반환한다 (빈 파트는 건너뜀). */
  private List<String> uploadPhotos(List<MultipartFile> photos) {
    if (photos == null) {
      return List.of();
    }
    List<String> urls = new ArrayList<>();
    for (MultipartFile photo : photos) {
      if (hasImage(photo)) {
        urls.add(uploadReviewImage(photo));
      }
    }
    return urls;
  }

  private String uploadReviewImage(MultipartFile image) {
    try {
      return storageService.upload(image);
    } catch (BusinessException e) {
      throw new BusinessException(ReviewErrorCode.REVIEW_IMAGE_UPLOAD_FAILED, e);
    }
  }

  private void addImages(Review review, List<String> urls) {
    for (int i = 0; i < urls.size(); i++) {
      review.addImage(ReviewImage.builder().review(review).url(urls.get(i)).sortOrder(i).build());
    }
  }

  private void validateImage(MultipartFile image) {
    if (!hasImage(image)) {
      return;
    }
    if (image.getSize() > MAX_IMAGE_BYTES) {
      throw new BusinessException(ReviewErrorCode.REVIEW_IMAGE_TOO_LARGE);
    }
    String contentType = image.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(ReviewErrorCode.REVIEW_IMAGE_INVALID_TYPE);
    }
    validateImageMagicBytes(image);
  }

  // Content-Type 헤더 스푸핑 방어 — 실제 파일 시그니처로 검증
  private void validateImageMagicBytes(MultipartFile image) {
    try (InputStream is = image.getInputStream()) {
      byte[] header = is.readNBytes(12);
      if (!isJpeg(header) && !isPng(header) && !isWebp(header)) {
        throw new BusinessException(ReviewErrorCode.REVIEW_IMAGE_INVALID_TYPE);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (IOException e) {
      throw new BusinessException(ReviewErrorCode.REVIEW_IMAGE_INVALID_TYPE);
    }
  }

  private boolean isJpeg(byte[] h) {
    return h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
  }

  private boolean isPng(byte[] h) {
    return h.length >= 4
        && (h[0] & 0xFF) == 0x89
        && (h[1] & 0xFF) == 0x50
        && (h[2] & 0xFF) == 0x4E
        && (h[3] & 0xFF) == 0x47;
  }

  // WebP: "RIFF"(0-3) + 파일크기(4-7) + "WEBP"(8-11)
  private boolean isWebp(byte[] h) {
    return h.length >= 12
        && (h[0] & 0xFF) == 0x52
        && (h[1] & 0xFF) == 0x49
        && (h[2] & 0xFF) == 0x46
        && (h[3] & 0xFF) == 0x46
        && (h[8] & 0xFF) == 0x57
        && (h[9] & 0xFF) == 0x45
        && (h[10] & 0xFF) == 0x42
        && (h[11] & 0xFF) == 0x50;
  }

  private boolean hasImage(MultipartFile image) {
    return image != null && !image.isEmpty();
  }
}
