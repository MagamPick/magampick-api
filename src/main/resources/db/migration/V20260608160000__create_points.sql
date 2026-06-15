-- Phase 7: 포인트 도메인 — point_accruals / point_transactions 생성
-- 의존: V20260607090000__create_orders.sql (orders, customers 선행)

-- ───────────────────────────────────────────────────────────────────────────────
-- point_accruals 테이블 (적립 건별 lot — balance source of truth)
-- ───────────────────────────────────────────────────────────────────────────────
CREATE TABLE point_accruals
(
    id               BIGSERIAL    PRIMARY KEY,
    customer_id      BIGINT       NOT NULL REFERENCES customers (id),
    order_id         BIGINT                REFERENCES orders (id),
    initial_amount   BIGINT       NOT NULL,
    remaining_amount BIGINT       NOT NULL,
    earned_at        TIMESTAMP    NOT NULL,
    expires_at       TIMESTAMP    NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_point_accruals_status CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED')),
    CONSTRAINT chk_point_accruals_remaining CHECK (remaining_amount >= 0 AND remaining_amount <= initial_amount)
);

CREATE INDEX idx_point_accruals_customer_status_earned
    ON point_accruals (customer_id, status, earned_at);

-- ───────────────────────────────────────────────────────────────────────────────
-- point_transactions 테이블 (내역 — 5사유)
-- ───────────────────────────────────────────────────────────────────────────────
CREATE TABLE point_transactions
(
    id          BIGSERIAL    PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id),
    order_id    BIGINT                REFERENCES orders (id),
    reason      VARCHAR(20)  NOT NULL,
    amount      BIGINT       NOT NULL,
    store_name  VARCHAR(100),
    occurred_at TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_point_transactions_reason CHECK (reason IN ('EARN', 'USE', 'EXPIRE', 'RESTORE', 'CLAWBACK')),
    CONSTRAINT chk_point_transactions_amount CHECK (amount > 0)
);

CREATE INDEX idx_point_transactions_customer_occurred
    ON point_transactions (customer_id, occurred_at DESC);
