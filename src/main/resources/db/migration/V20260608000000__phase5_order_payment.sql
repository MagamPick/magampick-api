-- Phase 5A: 주문 생성 + 주문 결제(stub)
-- orders status 7값 확장, 결제/픽업 컬럼 추가, order_items 스냅샷 컬럼 추가, payments 신규 생성

-- ───────────────────────────────────────────────────────────────────────────────
-- 1. orders: 기존 status CHECK 제약 먼저 제거 — 이후 데이터 이관 시 구 CHECK 위반 방지
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;

-- ───────────────────────────────────────────────────────────────────────────────
-- 2. orders: status 데이터 이관 (제약 없는 상태에서 안전하게 수행)
-- ───────────────────────────────────────────────────────────────────────────────
UPDATE orders SET status = 'PENDING'   WHERE status = 'RECEIVED';
UPDATE orders SET status = 'COMPLETED' WHERE status = 'PICKED_UP';
UPDATE orders SET status = 'CANCELLED' WHERE status = 'CANCELED';

-- ───────────────────────────────────────────────────────────────────────────────
-- 3. orders: 새 status CHECK 제약 추가 (7값)
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'PREPARING', 'READY', 'COMPLETED', 'NO_SHOW', 'REJECTED', 'CANCELLED'));

-- ───────────────────────────────────────────────────────────────────────────────
-- 4. orders: 신규 컬럼 추가
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE orders
    ADD COLUMN pickup_type    VARCHAR(10),
    ADD COLUMN pickup_code    VARCHAR(4),
    ADD COLUMN memo           VARCHAR(80),
    ADD COLUMN normal_total   NUMERIC(12, 0),
    ADD COLUMN discount_total NUMERIC(12, 0);

ALTER TABLE orders ADD CONSTRAINT chk_orders_pickup_type
    CHECK (pickup_type IS NULL OR pickup_type IN ('ASAP', 'SLOT'));

-- ───────────────────────────────────────────────────────────────────────────────
-- 5. order_items: clearance_item_id NOT NULL 해제 (DEAL/MENU 혼합 지원)
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE order_items ALTER COLUMN clearance_item_id DROP NOT NULL;

-- ───────────────────────────────────────────────────────────────────────────────
-- 6. order_items: 스냅샷 컬럼 + product FK 추가
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE order_items
    ADD COLUMN product_id     BIGINT,
    ADD COLUMN item_kind      VARCHAR(10),
    ADD COLUMN name           VARCHAR(255),
    ADD COLUMN original_price NUMERIC(12, 0),
    ADD COLUMN image_url      VARCHAR(500);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id);

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_item_kind
        CHECK (item_kind IS NULL OR item_kind IN ('DEAL', 'MENU'));

-- 정확히 clearance_item_id 또는 product_id 중 하나만 NOT NULL
ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_item_ref
        CHECK (
            (clearance_item_id IS NOT NULL AND product_id IS NULL) OR
            (clearance_item_id IS NULL AND product_id IS NOT NULL)
        );

CREATE INDEX idx_order_items_product_id ON order_items (product_id);

-- ───────────────────────────────────────────────────────────────────────────────
-- 7. payments 테이블 신규 생성 (stub 결제, 1:1 with orders)
-- ───────────────────────────────────────────────────────────────────────────────
CREATE TABLE payments
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT         NOT NULL,
    provider    VARCHAR(10)    NOT NULL,
    method      VARCHAR(20)    NOT NULL,
    payment_key VARCHAR(200)   NOT NULL,
    amount      NUMERIC(12, 0) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    approved_at TIMESTAMP,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uq_payments_order_id
        UNIQUE (order_id),
    CONSTRAINT chk_payments_status
        CHECK (status IN ('APPROVED', 'FAILED', 'CANCELED')),
    CONSTRAINT chk_payments_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
