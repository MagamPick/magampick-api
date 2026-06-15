-- Phase 7: 쿠폰 도메인 — coupons (마스터) / user_coupons (인스턴스) 생성
-- 의존: V20260515190000__create_auth_tables.sql (customers 선행)

-- ───────────────────────────────────────────────────────────────────────────────
-- coupons 테이블 (쿠폰 마스터 / 템플릿)
-- ───────────────────────────────────────────────────────────────────────────────
CREATE TABLE coupons
(
    id             BIGSERIAL    PRIMARY KEY,
    kind           VARCHAR(20)  NOT NULL,
    label          VARCHAR(100) NOT NULL,
    discount_type  VARCHAR(20)  NOT NULL,
    discount_value INT          NOT NULL,
    min_order      INT          NOT NULL DEFAULT 0,
    valid_until    DATE,
    validity_days  INT,
    issue_limit    INT,
    issued_count   INT          NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_coupons_kind CHECK (kind IN ('SIGNUP', 'EVENT')),
    CONSTRAINT chk_coupons_discount_type CHECK (discount_type IN ('RATE', 'AMOUNT')),
    CONSTRAINT chk_coupons_issued_count CHECK (issued_count >= 0),
    CONSTRAINT chk_coupons_issue_limit CHECK (issue_limit IS NULL OR issue_limit >= 0)
);

-- ───────────────────────────────────────────────────────────────────────────────
-- user_coupons 테이블 (소비자별 발급 인스턴스)
-- ───────────────────────────────────────────────────────────────────────────────
CREATE TABLE user_coupons
(
    id          BIGSERIAL    PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id),
    coupon_id   BIGINT       NOT NULL REFERENCES coupons (id),
    status      VARCHAR(20)  NOT NULL DEFAULT 'USABLE',
    expires_at  DATE         NOT NULL,
    issued_at   TIMESTAMP    NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_coupons_customer_coupon UNIQUE (customer_id, coupon_id),
    CONSTRAINT chk_user_coupons_status CHECK (status IN ('USABLE', 'USED', 'EXPIRED'))
);

CREATE INDEX idx_user_coupons_customer ON user_coupons (customer_id);

-- 가입 축하 쿠폰 SIGNUP 마스터 seed
-- RATE 20% · 최소주문 5,000원 · 유효기간 가입일+30일
INSERT INTO coupons (kind, label, discount_type, discount_value, min_order, validity_days, issue_limit, active)
VALUES ('SIGNUP', '신규 가입 축하 쿠폰', 'RATE', 20, 5000, 30, NULL, TRUE);
