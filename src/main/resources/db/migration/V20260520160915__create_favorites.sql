CREATE TABLE favorites
(
    id          BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT    NOT NULL REFERENCES customers (id),
    store_id    BIGINT    NOT NULL REFERENCES stores (id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_favorites_customer_store UNIQUE (customer_id, store_id)
);
