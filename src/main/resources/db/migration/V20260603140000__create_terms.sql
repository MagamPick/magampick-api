-- 약관 마스터 + 소비자 동의 기록. 회원가입(소비자) 1단계 약관 동의 흐름의 기반.
-- 약관 관리 UI 는 백로그 — 초기 약관은 아래 seed, 운영자가 SQL 로 갱신/신규 버전 추가.

CREATE TABLE terms
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type       VARCHAR(30)  NOT NULL,
    version    INT          NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    required   BOOLEAN      NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT chk_terms_type
        CHECK (type IN ('TERMS_OF_SERVICE', 'PRIVACY', 'LOCATION', 'AGE_14', 'MARKETING')),
    CONSTRAINT chk_terms_version_positive
        CHECK (version > 0),
    CONSTRAINT uk_terms_type_version
        UNIQUE (type, version)
);

-- 초기 약관 seed (version 1). body 는 placeholder — 운영자가 실제 약관 본문으로 갱신한다.
INSERT INTO terms (type, version, title, body, required, created_at, updated_at)
VALUES
    ('TERMS_OF_SERVICE', 1, '서비스 이용약관', '서비스 이용약관 본문 (운영자 갱신 예정)', TRUE, NOW(), NOW()),
    ('PRIVACY', 1, '개인정보 수집·이용 동의', '개인정보 수집·이용 동의 본문 (운영자 갱신 예정)', TRUE, NOW(), NOW()),
    ('LOCATION', 1, '위치 기반 서비스 이용약관', '위치 기반 서비스 이용약관 본문 (운영자 갱신 예정)', TRUE, NOW(), NOW()),
    ('AGE_14', 1, '만 14세 이상입니다', '만 14세 이상 확인 (개인정보보호법 제22조의2)', TRUE, NOW(), NOW()),
    ('MARKETING', 1, '마케팅 정보 수신 동의', '떨이·이벤트·쿠폰·혜택 등 광고성 정보 수신 동의 (선택)', FALSE, NOW(), NOW());

CREATE TABLE customer_terms_agreements
(
    id          BIGINT    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT    NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    term_id     BIGINT    NOT NULL REFERENCES terms (id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_customer_terms_agreements_customer_term UNIQUE (customer_id, term_id)
);
