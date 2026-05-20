ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP NULL;

ALTER TABLE products DROP CONSTRAINT uq_products_store_name;
CREATE UNIQUE INDEX uq_products_store_name_active
    ON products (store_id, name) WHERE deleted_at IS NULL;
