-- 리뷰 목록 조회 (Phase 4 read-only 구현): reviews + review_images + review_replies + review_tags
-- write (작성/수정/삭제/답글)는 Phase 7 구현 예정

CREATE TABLE reviews
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT        NOT NULL,
    order_id    BIGINT        NOT NULL,
    store_id    BIGINT        NOT NULL,
    rating      INT           NOT NULL,
    content     VARCHAR(300),
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_reviews_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_reviews_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_reviews_store
        FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT uq_reviews_order
        UNIQUE (order_id),
    CONSTRAINT chk_reviews_rating
        CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_reviews_store_id ON reviews (store_id);
CREATE INDEX idx_reviews_customer_id ON reviews (customer_id);

CREATE TABLE review_images
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id  BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL,
    CONSTRAINT fk_review_images_review
        FOREIGN KEY (review_id) REFERENCES reviews (id)
);

CREATE INDEX idx_review_images_review_id ON review_images (review_id);

CREATE TABLE review_replies
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id  BIGINT       NOT NULL,
    seller_id  BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT fk_review_replies_review
        FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_replies_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT uq_review_replies_review
        UNIQUE (review_id)
);

-- review_tags: @ElementCollection 매핑 (PK 없음 — Hibernate CollectionTable 표준)
CREATE TABLE review_tags
(
    review_id BIGINT      NOT NULL,
    tag       VARCHAR(20) NOT NULL,
    CONSTRAINT fk_review_tags_review
        FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT chk_review_tags_tag
        CHECK (tag IN ('FRESH', 'KIND', 'REVISIT', 'GENEROUS', 'GOOD_VALUE'))
);

CREATE INDEX idx_review_tags_review_id ON review_tags (review_id);
