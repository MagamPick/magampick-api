CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    regular_price NUMERIC(12, 0) NOT NULL,
    image_url VARCHAR(500),
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_products_store
        FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT chk_products_status
        CHECK (status IN ('ON_SALE', 'SOLD_OUT')),
    CONSTRAINT chk_products_regular_price_positive
        CHECK (regular_price > 0),
    CONSTRAINT uq_products_store_name
        UNIQUE (store_id, name)
);

CREATE INDEX idx_products_store_id ON products(store_id);
