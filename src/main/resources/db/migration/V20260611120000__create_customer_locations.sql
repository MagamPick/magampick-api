-- 소비자 현재 위치 테이블. customer 1:1 (natural key = PK).
-- 프론트엔드가 주기적으로 PUT /customers/me/location 을 호출해 갱신.
-- 떨이 등록·마감임박 알림 시 1시간 이내 갱신된 소비자를 ② 현재위치 반경 3km 대상으로 포함.

CREATE TABLE customer_locations (
    customer_id         BIGINT PRIMARY KEY,
    location            GEOGRAPHY(POINT, 4326) NOT NULL,
    location_updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_locations_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

-- 반경 검색용 GIST 인덱스 (ST_DWithin)
CREATE INDEX gix_customer_locations_location ON customer_locations USING GIST (location);
