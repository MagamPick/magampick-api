package com.magampick.review.repository;

import com.magampick.review.domain.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

  boolean existsByReviewId(Long reviewId);
}
