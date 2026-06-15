-- Phase 5 주문 전체 구현 전 스키마 선반영: orders + order_items (전이 로직/결제 컬럼 없음 — read-only)
-- 결제/포인트 컬럼(discount_amount, point_used, final_price)은 Phase 6 ALTER 로 추가 예정

CREATE TABLE orders
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT         NOT NULL,
    store_id    BIGINT         NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    total_price NUMERIC(12, 0) NOT NULL,
    pickup_time TIMESTAMP,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_orders_customer
        FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_orders_store
        FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT chk_orders_status
        CHECK (status IN ('RECEIVED', 'PREPARING', 'READY', 'PICKED_UP', 'CANCELED')),
    CONSTRAINT chk_orders_total_price_nonneg
        CHECK (total_price >= 0)
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_store_id ON orders (store_id);

CREATE TABLE order_items
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id          BIGINT         NOT NULL,
    clearance_item_id BIGINT         NOT NULL,
    quantity          INT            NOT NULL,
    unit_price        NUMERIC(12, 0) NOT NULL,
    subtotal          NUMERIC(12, 0) NOT NULL,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP      NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_clearance_item
        FOREIGN KEY (clearance_item_id) REFERENCES clearance_items (id),
    CONSTRAINT chk_order_items_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price_positive
        CHECK (unit_price > 0),
    CONSTRAINT chk_order_items_subtotal_positive
        CHECK (subtotal > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_clearance_item_id ON order_items (clearance_item_id);
