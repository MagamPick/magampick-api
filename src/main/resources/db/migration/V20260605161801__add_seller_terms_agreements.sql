-- 사장 가입 약관: 만 19세 확인 + 사장 약관 동의 기록 테이블.
-- 기존 terms / customer_terms_agreements 마이그레이션은 머지된 파일이므로 새 V 파일로 확장한다.

ALTER TABLE terms
    DROP CONSTRAINT chk_terms_type;

ALTER TABLE terms
    ADD CONSTRAINT chk_terms_type
        CHECK (type IN ('TERMS_OF_SERVICE', 'PRIVACY', 'LOCATION', 'AGE_14', 'AGE_19', 'MARKETING'));

INSERT INTO terms (type, version, title, body, required, created_at, updated_at)
VALUES ('AGE_19', 1, '만 19세 이상입니다', '만 19세 이상 확인 (사장 가입 자기 신고)', TRUE, NOW(), NOW())
ON CONFLICT (type, version) DO NOTHING;

CREATE TABLE seller_terms_agreements
(
    id         BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id  BIGINT    NOT NULL REFERENCES sellers (id) ON DELETE CASCADE,
    term_id    BIGINT    NOT NULL REFERENCES terms (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_seller_terms_agreements_seller_term UNIQUE (seller_id, term_id)
);
