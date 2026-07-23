-- moimyeon core 스키마 (단일 소스)
-- - 엔티티는 매핑만 담당하고, 테이블/타입/제약/인덱스는 이 파일에서 관리한다.
-- - 적용: 로컬/테스트(H2)는 spring.sql.init 으로 실행, 로컬 MySQL 은 docker-compose 의 initdb 로 로드.
-- - H2(MODE=MySQL)와 MySQL 8 모두에서 도는 공통 문법만 사용한다(ENGINE/CHARSET 등 방언 지시 없음).
-- - 재실행 안전을 위해 DROP IF EXISTS(자식→부모 순) 후 CREATE(부모→자식 순).
--   DROP 은 인메모리 H2 재초기화·MySQL 최초 initdb 용이다. 데이터가 있는 영속 DB 에
--   이 파일 전체를 수동 실행하면 파괴적이므로, 스키마 변경은 별도 절차(추후 마이그레이션)로 관리한다.

DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS example_entity;

CREATE TABLE member (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(320) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    last_login_at DATETIME     NOT NULL,
    withdrawn_at  DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE social_account (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    provider     VARCHAR(20)  NOT NULL,
    provider_id  VARCHAR(100) NOT NULL,
    linked_email VARCHAR(320) NULL,
    member_id    BINARY(16)   NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_account_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT fk_social_account_member FOREIGN KEY (member_id) REFERENCES member (id)
);
CREATE INDEX ix_social_account_member_id ON social_account (member_id);

CREATE TABLE refresh_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64)  NOT NULL,
    member_id  BINARY(16)   NOT NULL,
    expires_at DATETIME     NOT NULL,
    revoked_at DATETIME     NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id)
);
CREATE INDEX ix_refresh_token_member_id ON refresh_token (member_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);

CREATE TABLE example_entity (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    example_column VARCHAR(255) NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
