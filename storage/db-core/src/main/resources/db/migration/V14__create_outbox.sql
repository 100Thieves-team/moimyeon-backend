-- 외부 전달 전까지 보관하는 범용 내구성 작업 기록.
-- 업무 데이터의 소프트 삭제 정책이 아니라 전달 생명주기와 별도 보존 정책을 따른다.
CREATE TABLE outbox (
    id         BINARY(16)   NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload    TEXT         NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_outbox_created_at ON outbox (created_at);
