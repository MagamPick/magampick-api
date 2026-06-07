-- terms 표시 순서 컬럼 추가. 나이확인 → 이용약관 → 개인정보 → 위치 → 마케팅 순.
ALTER TABLE terms
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE terms SET sort_order = 1 WHERE type IN ('AGE_14', 'AGE_19');
UPDATE terms SET sort_order = 2 WHERE type = 'TERMS_OF_SERVICE';
UPDATE terms SET sort_order = 3 WHERE type = 'PRIVACY';
UPDATE terms SET sort_order = 4 WHERE type = 'LOCATION';
UPDATE terms SET sort_order = 5 WHERE type = 'MARKETING';

ALTER TABLE terms
    ALTER COLUMN sort_order DROP DEFAULT;
