CREATE TABLE customer_notification_settings (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE,
    nearby_deal BOOLEAN NOT NULL DEFAULT TRUE,
    favorite_store BOOLEAN NOT NULL DEFAULT TRUE,
    order_refund BOOLEAN NOT NULL DEFAULT TRUE,
    review_reply BOOLEAN NOT NULL DEFAULT TRUE,
    event_benefit BOOLEAN NOT NULL DEFAULT FALSE,
    marketing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cns_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE seller_notification_settings (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL UNIQUE,
    new_order BOOLEAN NOT NULL DEFAULT TRUE,
    order_cancel BOOLEAN NOT NULL DEFAULT TRUE,
    refund_request BOOLEAN NOT NULL DEFAULT TRUE,
    new_review BOOLEAN NOT NULL DEFAULT TRUE,
    notice BOOLEAN NOT NULL DEFAULT TRUE,
    marketing BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sns_seller FOREIGN KEY (seller_id) REFERENCES sellers(id)
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    receiver_type VARCHAR(10) NOT NULL,
    receiver_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_receiver_type CHECK (receiver_type IN ('CUSTOMER', 'SELLER')),
    CONSTRAINT chk_category CHECK (category IN ('DEAL','ORDER','REVIEW','BENEFIT','SYSTEM','REFUND','SETTLEMENT','NOTICE'))
);

CREATE INDEX idx_notifications_receiver ON notifications(receiver_type, receiver_id, created_at DESC);
CREATE INDEX idx_notifications_receiver_category ON notifications(receiver_type, receiver_id, category, created_at DESC);
