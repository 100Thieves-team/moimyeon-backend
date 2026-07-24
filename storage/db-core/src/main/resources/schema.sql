-- moimyeon core 스키마 (단일 소스)
-- - 엔티티는 매핑만 담당하고, 테이블/타입/제약/인덱스는 이 파일에서 관리한다.
-- - 적용: 로컬/테스트(H2)는 spring.sql.init 으로 실행, 로컬 MySQL 은 docker-compose 의 initdb 로 로드.
-- - H2(MODE=MySQL)와 MySQL 8 모두에서 도는 공통 문법만 사용한다(ENGINE/CHARSET 등 방언 지시 없음).
-- - 재실행 안전을 위해 DROP IF EXISTS(자식→부모 순) 후 CREATE(부모→자식 순).
--   DROP 은 인메모리 H2 재초기화·MySQL 최초 initdb 용이다. 데이터가 있는 영속 DB 에
--   이 파일 전체를 수동 실행하면 파괴적이므로, 스키마 변경은 별도 절차(추후 마이그레이션)로 관리한다.

DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS terms_agreement;
DROP TABLE IF EXISTS member_profile;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS terms;
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

CREATE TABLE member_profile (
    member_id          BINARY(16)   NOT NULL,
    nickname           VARCHAR(30)  NOT NULL,
    job_title          VARCHAR(100) NULL,
    bio                VARCHAR(500) NULL,
    meeting_preference VARCHAR(20)  NULL,
    region             VARCHAR(50)  NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT uk_member_profile_nickname UNIQUE (nickname),
    CONSTRAINT fk_member_profile_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE TABLE terms (
    id             BINARY(16)   NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    version        VARCHAR(20)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    required       BOOLEAN      NOT NULL,
    effective_from DATETIME     NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_type_version UNIQUE (type, version)
);

-- 동의 이력은 append-only: 애플리케이션에서 UPDATE/DELETE 를 수행하지 않는다.
CREATE TABLE terms_agreement (
    id         BINARY(16) NOT NULL,
    member_id  BINARY(16) NOT NULL,
    terms_id   BINARY(16) NOT NULL,
    agreed_at  DATETIME   NOT NULL,
    created_at DATETIME   NOT NULL,
    updated_at DATETIME   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_agreement_member_terms UNIQUE (member_id, terms_id),
    CONSTRAINT fk_terms_agreement_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_terms_agreement_terms FOREIGN KEY (terms_id) REFERENCES terms (id)
);
CREATE INDEX ix_terms_agreement_member_id ON terms_agreement (member_id);

CREATE TABLE example_entity (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    example_column VARCHAR(255) NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
