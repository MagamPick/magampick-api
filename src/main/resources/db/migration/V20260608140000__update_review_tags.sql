-- review_tags 프리셋 변경: 5종 → 7종, REVISIT→REORDER 리네임
-- 기존: FRESH, KIND, REVISIT, GENEROUS, GOOD_VALUE
-- 변경: DELICIOUS, FRESH, REORDER, FAST_PICKUP, GENEROUS, GOOD_VALUE, KIND

-- 기존 REVISIT 데이터 마이그레이션
UPDATE review_tags SET tag = 'REORDER' WHERE tag = 'REVISIT';

-- CHECK 제약 교체
ALTER TABLE review_tags DROP CONSTRAINT chk_review_tags_tag;
ALTER TABLE review_tags
    ADD CONSTRAINT chk_review_tags_tag
        CHECK (tag IN ('DELICIOUS', 'FRESH', 'REORDER', 'FAST_PICKUP', 'GENEROUS', 'GOOD_VALUE', 'KIND'));
