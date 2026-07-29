-- moimyeon core 스키마 (단일 소스)
-- - 엔티티는 매핑만 담당하고, 테이블/타입/제약/인덱스는 이 파일에서 관리한다(유니크만 엔티티에도 선언).
-- - 적용: 로컬/테스트(H2)는 spring.sql.init 으로 실행, 로컬 MySQL 은 docker-compose 의 initdb 로 로드.
-- - H2(MODE=MySQL)와 MySQL 8 모두에서 도는 공통 문법만 사용한다(ENGINE/CHARSET 등 방언 지시 없음).
-- - FK 제약은 걸지 않는다. 참조 무결성은 애플리케이션의 저장 전 명시 검증이 담당한다
--   (docs/conventions/storage.md). 테이블 간 참조는 *_id 컬럼 + 필요 시 인덱스로만 표현한다.
-- - 재실행 안전을 위해 DROP IF EXISTS 후 CREATE. DROP 은 인메모리 H2 재초기화·MySQL 최초
--   initdb 용이다. 데이터가 있는 영속 DB 에 이 파일 전체를 수동 실행하면 파괴적이므로,
--   스키마 변경은 별도 절차(추후 마이그레이션)로 관리한다.

DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS terms_agreement;
DROP TABLE IF EXISTS member_profile_interest;
DROP TABLE IF EXISTS member_profile;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS terms;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS job_role;
DROP TABLE IF EXISTS job_group;
DROP TABLE IF EXISTS sigungu;
DROP TABLE IF EXISTS sido;
DROP TABLE IF EXISTS company;
DROP TABLE IF EXISTS example_entity;

-- ── 참조 데이터(크롤러 소유) ─────────────────────────────────────────────
-- deleted_at: 재크롤 미출현 시 크롤러가 세팅(NULL=유효, 소프트 삭제). updated_at 의 ON UPDATE CURRENT_TIMESTAMP 는
-- MySQL 전용 문법이라 제외 — 갱신 주체(크롤러)가 직접 세팅한다.

-- 지역-시도 마스터(법정동 2026)
CREATE TABLE sido (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    sido_code  CHAR(2)     NOT NULL,
    name       VARCHAR(20) NOT NULL,
    short_name VARCHAR(10) NOT NULL,
    is_metro   BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order SMALLINT    NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sido_sido_code UNIQUE (sido_code),
    CONSTRAINT uk_sido_name UNIQUE (name),
    CONSTRAINT uk_sido_short_name UNIQUE (short_name)
);

-- 지역-시군구 마스터(법정동 2026)
CREATE TABLE sigungu (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    sido_id      BIGINT      NOT NULL,
    sigungu_code CHAR(5)     NULL,
    name         VARCHAR(40) NOT NULL,
    level        VARCHAR(10) NOT NULL,
    sort_order   SMALLINT    NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   DATETIME    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sigungu_sigungu_code UNIQUE (sigungu_code),
    CONSTRAINT uk_sigungu_sido_name UNIQUE (sido_id, name)
);
CREATE INDEX ix_sigungu_sido_id ON sigungu (sido_id);

-- 직업유형-직군
CREATE TABLE job_group (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    code         VARCHAR(40) NOT NULL,
    display_name VARCHAR(60) NOT NULL,
    sort_order   SMALLINT    NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   DATETIME    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_job_group_code UNIQUE (code)
);

-- 직업유형-직무
CREATE TABLE job_role (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    job_group_id BIGINT      NOT NULL,
    code         VARCHAR(60) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    sort_order   SMALLINT    NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   DATETIME    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_job_role_code UNIQUE (code)
);
CREATE INDEX ix_job_role_job_group_id ON job_role (job_group_id);

-- 회사 (corp_code = DART 고유번호, 식별 앵커)
CREATE TABLE company (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    corp_code       CHAR(8)      NULL,
    name_kr         VARCHAR(200) NOT NULL,
    name_normalized VARCHAR(200) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_corp_code UNIQUE (corp_code)
);
CREATE INDEX ix_company_name_normalized ON company (name_normalized);

-- nickname 은 가입 시 서버가 자동 생성하는 표시 이름(회원 신원의 일부, 전역 유일)
CREATE TABLE member (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(320) NOT NULL,
    nickname      VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    last_login_at DATETIME     NOT NULL,
    withdrawn_at  DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
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
    CONSTRAINT uk_social_account_provider_provider_id UNIQUE (provider, provider_id)
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
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_member_id ON refresh_token (member_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);

-- 소개 정보(전부 선택 입력). 행 존재 = 온보딩(최초 소개 작성) 제출 완료라는 파생 사실의 근거.
CREATE TABLE member_profile (
    member_id          BINARY(16)   NOT NULL,
    job_role_id        BIGINT       NULL,
    bio                VARCHAR(500) NULL,
    meeting_preference VARCHAR(20)  NULL,
    sigungu_id         BIGINT       NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    PRIMARY KEY (member_id)
);

-- 관심 회사(company 참조 다건). 프로필과 생명주기를 같이하는 값 컬렉션이라 별도 PK 없이 쌍 유니크만 둔다.
CREATE TABLE member_profile_interest (
    member_id  BINARY(16) NOT NULL,
    company_id BIGINT     NOT NULL,
    CONSTRAINT uk_member_profile_interest UNIQUE (member_id, company_id)
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
    CONSTRAINT uk_terms_agreement_member_terms UNIQUE (member_id, terms_id)
);
CREATE INDEX ix_terms_agreement_member_id ON terms_agreement (member_id);

CREATE TABLE example_entity (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    example_column VARCHAR(255) NOT NULL,
    deleted_at     DATETIME     NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
