CREATE TABLE store_categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE stores (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    road_address VARCHAR(200) NOT NULL,
    jibun_address VARCHAR(200),
    detail_address VARCHAR(100),
    zonecode VARCHAR(10) NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    image_url VARCHAR(500) NOT NULL,
    status VARCHAR(10) NOT NULL,
    rejection_reason VARCHAR(500),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_stores_seller
        FOREIGN KEY (seller_id) REFERENCES sellers(id),
    CONSTRAINT chk_stores_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE store_store_categories (
    store_id BIGINT NOT NULL,
    store_category_id BIGINT NOT NULL,
    PRIMARY KEY (store_id, store_category_id),
    CONSTRAINT fk_ssc_store
        FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_ssc_category
        FOREIGN KEY (store_category_id) REFERENCES store_categories(id)
);

CREATE INDEX idx_stores_seller_id ON stores(seller_id);
CREATE INDEX idx_stores_status ON stores(status);
CREATE INDEX idx_stores_location ON stores USING GIST(location);

INSERT INTO store_categories (name, created_at, updated_at)
VALUES
    ('베이커리', NOW(), NOW()),
    ('카페', NOW(), NOW());
