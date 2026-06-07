-- Phase 6: 정산 도메인 — settlements 생성
-- 의존: V20260607100000__create_refunds.sql (feat/refund 선행 머지 필요)
-- 집계 쿼리에서 refunds 테이블 LEFT JOIN 사용

-- ───────────────────────────────────────────────────────────────────────────────
-- settlements 테이블 (반월(half) 단위 정산 회차. store × year × month × half 유니크)
-- ───────────────────────────────────────────────────────────────────────────────
CREATE TABLE settlements
(
    id           BIGSERIAL PRIMARY KEY,
    store_id     BIGINT          NOT NULL REFERENCES stores (id),
    year         INT             NOT NULL,
    month        INT             NOT NULL,
    half         INT             NOT NULL,
    period_start DATE            NOT NULL,
    period_end   DATE            NOT NULL,
    deposit_date DATE            NOT NULL,
    gross_amount DECIMAL(15, 2)  NOT NULL DEFAULT 0,
    fee_amount   DECIMAL(15, 2)  NOT NULL DEFAULT 0,
    net_amount   DECIMAL(15, 2)  NOT NULL DEFAULT 0,
    status       VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
    created_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_settlements_store_period UNIQUE (store_id, year, month, half),
    CONSTRAINT chk_settlements_status CHECK (status IN ('SCHEDULED', 'DEPOSITED')),
    CONSTRAINT chk_settlements_half CHECK (half IN (1, 2))
);

CREATE INDEX idx_settlements_store_id ON settlements (store_id);
