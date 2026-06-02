-- 매장 등록 신청 재설계 (경로 B)
-- per-store 사업자 번호 추가 / 관리자 승인(status·rejection_reason) 제거 / 매장 카테고리 제거

-- 1) per-store 사업자 번호 (UNIQUE 미적용 — 동일 번호 중복 허용)
ALTER TABLE stores ADD COLUMN business_number VARCHAR(10) NOT NULL;

-- 2) 관리자 승인 흐름 제거 → 자동 승인 전환
DROP INDEX IF EXISTS idx_stores_status;
ALTER TABLE stores DROP CONSTRAINT IF EXISTS chk_stores_status;
ALTER TABLE stores DROP COLUMN IF EXISTS status;
ALTER TABLE stores DROP COLUMN IF EXISTS rejection_reason;

-- 3) 매장 카테고리 제거 (매장엔 업종 카테고리 없음 — 상품 카테고리는 별도)
DROP TABLE IF EXISTS store_store_categories;
DROP TABLE IF EXISTS store_categories;
