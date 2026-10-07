-- V8 — 이력서 식별자를 공개 API 계약과 같은 UUID 로 바꾸고 보관함 규칙을 이력서 행에 표현한다.
--
-- resume 은 V4 에서 앞으로 구현될 룸 기능을 위해 미리 생성됐지만 MOI-377 이전에는 쓰기 API와
-- Entity 가 없었다. 따라서 dev 의 resume·resume_submission 은 비어 있어야 한다. 이 전제가 깨지면
-- 데이터를 잃지 않도록 존재하지 않는 테이블 조회로 마이그레이션을 중단한다.

SET @rows_in_resume_tables := (
    (SELECT COUNT(*) FROM resume)
  + (SELECT COUNT(*) FROM resume_submission)
);

SET @guard := IF(
    @rows_in_resume_tables > 0,
    'SELECT 1 FROM ABORT_resume_tables_must_be_empty_rewrite_V8_as_ALTER',
    'SELECT 1'
);

PREPARE guard_stmt FROM @guard;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

DROP TABLE IF EXISTS resume_submission;
DROP TABLE IF EXISTS resume;

CREATE TABLE resume (
    id              BINARY(16)   NOT NULL,
    member_id       BINARY(16)   NOT NULL,
    name            VARCHAR(100) NOT NULL,
    file_key        VARCHAR(500) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    summary_content TEXT         NULL,
    summary_status  VARCHAR(20)  NOT NULL,
    summary_started_at DATETIME(6) NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    _default_member_id BINARY(16) GENERATED ALWAYS AS (
        CASE WHEN is_default = TRUE AND deleted_at IS NULL THEN member_id ELSE NULL END
    ),
    PRIMARY KEY (id),
    CONSTRAINT uk_resume_member_default UNIQUE (_default_member_id)
);
CREATE INDEX ix_resume_member_id ON resume (member_id);

CREATE TABLE resume_submission (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    room_id       BINARY(16) NOT NULL,
    member_id     BINARY(16) NOT NULL,
    resume_id     BINARY(16) NOT NULL,
    submitted_at  DATETIME   NOT NULL,
    created_at    DATETIME   NOT NULL,
    updated_at    DATETIME   NOT NULL,
    deleted_at    DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_resume_submission_room_member_active UNIQUE (room_id, member_id, _active_check)
);
CREATE INDEX ix_resume_submission_member_id ON resume_submission (member_id);
