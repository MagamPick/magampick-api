-- 소비자 주소지 (customer 1:N). 최대 3개 보유 (앱 레벨 제약).
-- 알림 발송 fallback 채널 및 미래 탐색/매장 추천 기준의 데이터 공급.

CREATE TABLE addresses (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    label VARCHAR(20) NOT NULL,
    road_address VARCHAR(200) NOT NULL,
    jibun_address VARCHAR(200),
    detail_address VARCHAR(100),
    zonecode VARCHAR(10),
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_addresses_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    CONSTRAINT chk_addresses_label_not_blank
        CHECK (length(trim(label)) >= 1),
    CONSTRAINT chk_addresses_road_address_not_blank
        CHECK (length(trim(road_address)) >= 1),
    CONSTRAINT chk_addresses_zonecode_format
        CHECK (zonecode IS NULL OR zonecode ~ '^[0-9]{5}$')
);

-- 소비자별 주소 목록 조회 (FK 조회 패턴)
CREATE INDEX idx_addresses_customer_id ON addresses(customer_id);

-- customer 당 default 정확히 0 또는 1 강제 (부분 UNIQUE 인덱스)
CREATE UNIQUE INDEX uq_addresses_customer_default
    ON addresses(customer_id) WHERE is_default = TRUE;

-- PostGIS 반경 검색 대비 (미래 계층 4 / 8-A 활용)
CREATE INDEX gix_addresses_location ON addresses USING GIST (location);
