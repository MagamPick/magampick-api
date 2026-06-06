-- 상품 카테고리 컬럼 추가. 기존 상품은 기타(ETC)로 초기화.
ALTER TABLE products ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'ETC';
ALTER TABLE products ADD CONSTRAINT chk_products_category CHECK (category IN ('BAKERY','BEVERAGE','DESSERT','ETC'));
