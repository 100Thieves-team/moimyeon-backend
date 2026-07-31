-- V3 — 회원계 테이블을 소프트 삭제 기준으로 재생성한다
--
-- dev 는 소프트 삭제 리팩터(9e1767f · b06e7e3) 이전 상태에 멈춰 있다.
--   member          : withdrawn_at 이 있고 deleted_at 이 없다
--   member_profile  : 코드에 없는 nickname NOT NULL 컬럼이 남아 있다
--   terms           : deleted_at 이 없다
--   terms_agreement : deleted_at 도 _active_check 도 없고 유니크가 살아있는 행을 가리지 못한다
-- 이 상태로는 최신 코드가 ddl-auto=validate 에서 부팅하지 못한다.
--
-- 이 테이블들은 전부 0행이므로(2026-07-31 dev 확인) ALTER 를 길게 늘어놓는 대신 재생성한다.
-- 컬럼 순서·인덱스 이름까지 schema.sql 과 정확히 같아지고, 앱 테이블에 남아 있던 FK 제약도
-- 함께 사라진다(컨벤션: 참조 무결성은 애플리케이션이 검증한다).
--
-- ⚠️ 전제가 깨지면 데이터가 사라지므로, 아래 가드가 먼저 실행되어 마이그레이션을 중단시킨다.
--    한 행이라도 있으면 존재하지 않는 테이블을 SELECT 해 즉시 실패한다.
--    그때는 이 파일을 재생성이 아닌 ALTER 로 다시 써야 한다.

SET @rows_in_member_tables := (
    (SELECT COUNT(*) FROM member)
  + (SELECT COUNT(*) FROM member_profile)
  + (SELECT COUNT(*) FROM member_profile_interest)
  + (SELECT COUNT(*) FROM social_account)
  + (SELECT COUNT(*) FROM refresh_token)
  + (SELECT COUNT(*) FROM terms)
  + (SELECT COUNT(*) FROM terms_agreement)
);

SET @guard := IF(
    @rows_in_member_tables > 0,
    'SELECT 1 FROM ABORT_member_tables_must_be_empty_rewrite_V3_as_ALTER',
    'SELECT 1'
);

PREPARE guard_stmt FROM @guard;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

-- ── 재생성 (자식 → 부모 순으로 DROP) ────────────────────────────────────
DROP TABLE IF EXISTS member_profile_interest;
DROP TABLE IF EXISTS member_profile;
DROP TABLE IF EXISTS terms_agreement;
DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS terms;
DROP TABLE IF EXISTS member;

-- 탈퇴는 deleted_at 으로 표현한다. status 는 그와 직교하는 제재 상태(ACTIVE/RESTRICTED)만 담는다.
-- 닉네임 유니크는 탈퇴자를 포함한 전체 회원 대상이라 _active_check 를 쓰지 않는다.
CREATE TABLE member (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(320) NOT NULL,
    nickname      VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    last_login_at DATETIME     NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    deleted_at    DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
);

-- 베이스 미상속: 재가입 차단이 (provider, provider_id) 유니크 점유에 의존한다.
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

-- 베이스 미상속: 삭제가 아니라 무효화이며 revoked_at 이 그 시각을 갖는다.
CREATE TABLE refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    expires_at DATETIME    NOT NULL,
    revoked_at DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_member_id ON refresh_token (member_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);

-- nickname 컬럼이 사라졌다 — 닉네임은 member 가 갖는다(회원 신원의 일부).
-- 직무 단일 참조(job_role_id)도 사라졌다 — 관심 직무는 다건이라 아래 값 컬렉션으로 뺐다.
CREATE TABLE member_profile (
    member_id          BINARY(16)   NOT NULL,
    bio                VARCHAR(500) NULL,
    meeting_preference VARCHAR(20)  NULL,
    sigungu_id         BIGINT       NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    deleted_at         DATETIME     NULL,
    PRIMARY KEY (member_id)
);

-- member_profile_interest 에서 이름이 바뀌었다 — 관심 직무 조인이 생기면서 대상이 모호해졌다.
-- 값 컬렉션이 아니라 엔티티다: 프로필과 company/job_role 은 양쪽이 다 엔티티인 M:N 이라
-- 조인 테이블도 id·타임스탬프·deleted_at 을 갖는다.
CREATE TABLE member_profile_interest_company (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    member_id     BINARY(16) NOT NULL,
    company_id    BIGINT     NOT NULL,
    created_at    DATETIME   NOT NULL,
    updated_at    DATETIME   NOT NULL,
    deleted_at    DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_member_profile_interest_company_active UNIQUE (member_id, company_id, _active_check)
);
CREATE INDEX ix_member_profile_interest_company_company_id ON member_profile_interest_company (company_id);

CREATE TABLE member_profile_interest_job_role (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    member_id     BINARY(16) NOT NULL,
    job_role_id   BIGINT     NOT NULL,
    created_at    DATETIME   NOT NULL,
    updated_at    DATETIME   NOT NULL,
    deleted_at    DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_member_profile_interest_job_role_active UNIQUE (member_id, job_role_id, _active_check)
);
CREATE INDEX ix_member_profile_interest_job_role_job_role_id ON member_profile_interest_job_role (job_role_id);

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
    deleted_at     DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_type_version UNIQUE (type, version)
);

-- 재동의는 되살리기가 아니라 새 행으로 append 한다 — 언제 다시 동의했는지가 증빙의 핵심이다.
-- _active_check 가 그 append 를 가능하게 한다(유니크를 살아있는 행끼리만 걸어 준다).
CREATE TABLE terms_agreement (
    id            BINARY(16) NOT NULL,
    member_id     BINARY(16) NOT NULL,
    terms_id      BINARY(16) NOT NULL,
    agreed_at     DATETIME   NOT NULL,
    created_at    DATETIME   NOT NULL,
    updated_at    DATETIME   NOT NULL,
    deleted_at    DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_agreement_member_terms_active UNIQUE (member_id, terms_id, _active_check)
);
CREATE INDEX ix_terms_agreement_member_id ON terms_agreement (member_id);
