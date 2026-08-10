-- Redis 재전달 실행 권한이 겹쳐도 같은 Outbox를 동시에 처리하지 않도록
-- 짧은 DB 트랜잭션에서 선점 상태와 만료 시각을 기록한다.
ALTER TABLE outbox
    ADD COLUMN relay_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN claim_token VARCHAR(36) NULL,
    ADD COLUMN lease_until DATETIME NULL;

CREATE INDEX ix_outbox_relay_status_created_at ON outbox (relay_status, created_at);
