-- Phase 9 검색: pg_trgm 확장 + GIN 인덱스 추가
-- 서비스명 부분 일치(ILIKE) 및 자동완성(word_similarity) 쿼리 가속용

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_stores_name_trgm ON stores USING GIN (name gin_trgm_ops);
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
CREATE INDEX idx_clearance_items_name_trgm ON clearance_items USING GIN (name gin_trgm_ops);
