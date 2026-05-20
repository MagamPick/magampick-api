CREATE TABLE clearance_items
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id           BIGINT         NOT NULL,
    product_id         BIGINT,
    name               VARCHAR(50)    NOT NULL,
    regular_price      NUMERIC(12, 0) NOT NULL,
    sale_price         NUMERIC(12, 0) NOT NULL,
    total_quantity     INT            NOT NULL,
    remaining_quantity INT            NOT NULL,
    pickup_start_at    TIMESTAMP      NOT NULL,
    pickup_end_at      TIMESTAMP      NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    CONSTRAINT fk_clearance_items_store
        FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_clearance_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_clearance_items_status
        CHECK (status IN ('OPEN', 'SOLD_OUT', 'CLOSED')),
    CONSTRAINT chk_clearance_items_sale_price_positive
        CHECK (sale_price > 0),
    CONSTRAINT chk_clearance_items_regular_price_positive
        CHECK (regular_price > 0),
    CONSTRAINT chk_clearance_items_total_quantity_positive
        CHECK (total_quantity > 0),
    CONSTRAINT chk_clearance_items_remaining_quantity_nonneg
        CHECK (remaining_quantity >= 0)
);

CREATE INDEX idx_clearance_items_store_id ON clearance_items (store_id);
CREATE INDEX idx_clearance_items_product_id ON clearance_items (product_id);

-- 한 상품당 OPEN 상태의 마감 임박 상품은 1개로 제한
CREATE UNIQUE INDEX uq_clearance_items_product_open
    ON clearance_items (product_id) WHERE status = 'OPEN';
