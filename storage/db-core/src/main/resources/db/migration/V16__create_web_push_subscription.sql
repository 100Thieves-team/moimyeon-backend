-- 브라우저가 업로드한 현재 웹 푸시 등록과 마지막 동기화 시각을 보존한다.
-- 등록 식별자는 길이가 길 수 있어 원문 대신 SHA-256 해시에 유니크 제약을 둔다.
CREATE TABLE web_push_subscription (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    member_id         BINARY(16)  NOT NULL,
    registration      TEXT        NOT NULL,
    registration_hash CHAR(64)    NOT NULL,
    registered_at     DATETIME    NOT NULL,
    created_at        DATETIME    NOT NULL,
    updated_at        DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_web_push_subscription_registration_hash UNIQUE (registration_hash)
);
CREATE INDEX ix_web_push_subscription_member_id ON web_push_subscription (member_id);
