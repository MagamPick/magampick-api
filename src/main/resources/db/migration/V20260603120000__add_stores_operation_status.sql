-- 매장 영업 상태 컬럼 추가 (노션: 매장 영업 상태 관리). 등록 직후 CLOSED_TODAY 초기값.

ALTER TABLE stores ADD COLUMN operation_status VARCHAR(15);
UPDATE stores SET operation_status = 'CLOSED_TODAY' WHERE operation_status IS NULL;
ALTER TABLE stores ALTER COLUMN operation_status SET NOT NULL;
ALTER TABLE stores
    ADD CONSTRAINT chk_stores_operation_status
    CHECK (operation_status IN ('OPEN', 'BREAK', 'CLOSED_TODAY'));

CREATE INDEX idx_stores_operation_status ON stores(operation_status);
