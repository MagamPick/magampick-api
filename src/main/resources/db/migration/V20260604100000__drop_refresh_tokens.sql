-- refresh 토큰 저장을 DB 테이블 → Redis 로 이전 (로그인/로그아웃 명세: 키 refresh:{role}:{id}:{jti}).
-- refresh_tokens 테이블·인덱스 제거. 머지된 V20260515190000 은 수정하지 않고 새 파일로 drop.

DROP INDEX IF EXISTS idx_refresh_tokens_owner;
DROP TABLE IF EXISTS refresh_tokens;
