-- 매장 요일별 영업시간 (노션 "영업시간 설정"). 영업 요일만 row 저장 — 휴무 요일은 row 없음.

CREATE TABLE store_business_hours (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sbh_store
        FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT uq_sbh_store_dow
        UNIQUE (store_id, day_of_week),
    CONSTRAINT chk_sbh_day_of_week
        CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT chk_sbh_time_range
        CHECK (open_time < close_time)
);

CREATE INDEX idx_sbh_store_id ON store_business_hours(store_id);
