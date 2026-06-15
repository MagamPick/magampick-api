-- FCM 푸시 디바이스 토큰. 소유자 polymorphic (owner_type, owner_id) — 소비자/사장 공유 users 테이블 없음 → FK 미사용.
-- 같은 토큰은 한 행만(UNIQUE token) — 기기 공유/재로그인 시 소유자 재할당.

CREATE TABLE push_tokens
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_type VARCHAR(20)  NOT NULL,
    owner_id   BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT uk_push_tokens_token UNIQUE (token),
    CONSTRAINT chk_push_tokens_owner_type CHECK (owner_type IN ('CUSTOMER', 'SELLER')),
    CONSTRAINT chk_push_tokens_platform CHECK (platform IN ('WEB'))
);

-- 소유자별 토큰 조회 (발송 시 lookup)
CREATE INDEX idx_push_tokens_owner ON push_tokens (owner_type, owner_id);
