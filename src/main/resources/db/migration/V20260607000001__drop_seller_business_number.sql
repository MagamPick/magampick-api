-- sellers.business_number 제거 — 사업자 번호는 매장 단위(stores.business_number)로만 관리
ALTER TABLE sellers DROP COLUMN business_number;
