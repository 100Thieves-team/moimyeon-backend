-- V4 — 룸·면접 진행·신뢰·채팅 테이블을 만든다 (신규 20개)
--
-- 전부 CREATE 뿐이라 기존 데이터에 닿지 않는다.
-- 각 테이블이 왜 이런 모양인지(소프트 삭제 여부, _active_check 를 붙인 이유, 인덱스 기준)는
-- schema.sql 의 테이블 주석에 적혀 있다. 여기서는 중복하지 않고 요지만 남긴다.
--
-- _active_check 는 "살아있을 때만 값이 있는" 생성 컬럼이다. 유니크 인덱스가 NULL 을 서로 다른
-- 값으로 취급하는 성질을 이용해, 유니크를 살아있는 행끼리만 걸어 준다. 그래서 소프트 삭제 후
-- 같은 키로 다시 만들 수 있다. STORED/VIRTUAL 을 생략하는 것은 H2 호환(schema.sql)과 맞추기 위함이다.

-- ── 이력서 ──────────────────────────────────────────────────────────────
-- 등록 후 내용을 수정하지 않는다(바꾸려면 새로 등록). 이미 제출한 룸의 참여자가 본 것이 보존되어야 한다.
CREATE TABLE resume (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    member_id       BINARY(16)   NOT NULL,
    title           VARCHAR(100) NOT NULL,
    file_ref        VARCHAR(500) NULL,
    summary_content TEXT         NULL,
    summary_status  VARCHAR(20)  NOT NULL,
    archived_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    deleted_at      DATETIME     NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_resume_member_id ON resume (member_id);

-- ── 룸(모의면접) ────────────────────────────────────────────────────────
-- 방장은 participation.participation_role='HOST' 가 유일한 진실 원천이라 룸에 컬럼을 두지 않는다.
-- 회사도 두지 않는다 — job_posting 이 필수이므로 공고를 통해 알 수 있다.
-- job_posting_id 는 크롤러 job_posting.id 를 참조한다. 공고가 여러 직무에 걸릴 수 있어
-- (job_posting_role) 룸이 그중 하나를 job_role_id 로 고른다.
CREATE TABLE room (
    id               BINARY(16)   NOT NULL,
    job_posting_id   BIGINT       NOT NULL,
    job_role_id      BIGINT       NOT NULL,
    sigungu_id       BIGINT       NULL,
    title            VARCHAR(100) NOT NULL,
    description      TEXT         NULL,
    interview_stage  VARCHAR(20)  NOT NULL,
    interview_type   VARCHAR(20)  NULL,
    meeting_type     VARCHAR(20)  NOT NULL,
    min_capacity     SMALLINT     NOT NULL,
    max_capacity     SMALLINT     NOT NULL,
    start_at         DATETIME     NOT NULL,
    duration_minutes SMALLINT     NOT NULL,
    resume_public    BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    deleted_at       DATETIME     NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_room_job_posting_id ON room (job_posting_id);
CREATE INDEX ix_room_job_role_id ON room (job_role_id);
CREATE INDEX ix_room_sigungu_id ON room (sigungu_id);

-- 확정·완료를 누가 언제 했는가. (room_id, transition_type) 유니크가 그 처리의 멱등성을 보장한다.
CREATE TABLE room_status_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    room_id           BINARY(16)  NOT NULL,
    transition_type   VARCHAR(20) NOT NULL,
    handler_member_id BINARY(16)  NOT NULL,
    occurred_at       DATETIME    NOT NULL,
    created_at        DATETIME    NOT NULL,
    updated_at        DATETIME    NOT NULL,
    deleted_at        DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_status_log_room_transition_active UNIQUE (room_id, transition_type, _active_check)
);

-- 철회 후 재신청이 허용되므로 (room_id, applicant_member_id) 에는 유니크를 걸 수 없다.
-- "대기 상태인 신청은 룸당 회원당 1건"을 pending_member_id 로 표현한다(대기면 신청자 ID, 아니면 NULL).
CREATE TABLE room_application (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    room_id             BINARY(16)  NOT NULL,
    applicant_member_id BINARY(16)  NOT NULL,
    pending_member_id   BINARY(16)  NULL,
    note                TEXT        NULL,
    status              VARCHAR(20) NOT NULL,
    reject_reason       VARCHAR(50) NULL,
    handler_member_id   BINARY(16)  NULL,
    applied_at          DATETIME    NOT NULL,
    handled_at          DATETIME    NULL,
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    deleted_at          DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_application_room_pending_active UNIQUE (room_id, pending_member_id, _active_check)
);
CREATE INDEX ix_room_application_applicant_member_id ON room_application (applicant_member_id);

-- 수락되어 명단에 들어간 상태. 룸 생성 시 방장의 참여가 함께 만들어진다.
CREATE TABLE participation (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    room_id            BINARY(16)  NOT NULL,
    member_id          BINARY(16)  NOT NULL,
    participation_role VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    joined_at          DATETIME    NOT NULL,
    left_at            DATETIME    NULL,
    left_by_member_id  BINARY(16)  NULL,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    deleted_at         DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_participation_room_member_active UNIQUE (room_id, member_id, _active_check)
);
CREATE INDEX ix_participation_member_id ON participation (member_id);

-- 어떤 룸에 어떤 이력서를 냈는가. 원본 공개 여부는 룸의 속성(room.resume_public)이다.
CREATE TABLE resume_submission (
    id           BIGINT     NOT NULL AUTO_INCREMENT,
    room_id      BINARY(16) NOT NULL,
    member_id    BINARY(16) NOT NULL,
    resume_id    BIGINT     NOT NULL,
    submitted_at DATETIME   NOT NULL,
    created_at   DATETIME   NOT NULL,
    updated_at   DATETIME   NOT NULL,
    deleted_at   DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_resume_submission_room_member_active UNIQUE (room_id, member_id, _active_check)
);
CREATE INDEX ix_resume_submission_member_id ON resume_submission (member_id);

-- ── 면접 진행 ───────────────────────────────────────────────────────────
-- 진행 확정 이후에만 생성된다(룸과 선택적 1:1).
CREATE TABLE interview_plan (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    room_id          BINARY(16) NOT NULL,
    opening_minutes  SMALLINT   NOT NULL,
    closing_minutes  SMALLINT   NOT NULL,
    last_assigned_at DATETIME   NULL,
    created_at       DATETIME   NOT NULL,
    updated_at       DATETIME   NOT NULL,
    deleted_at       DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_plan_room_active UNIQUE (room_id, _active_check)
);

-- 한 사람의 면접 = 한 라운드. interviewee_member_id 가 NULL 이면 확정 후 취소로 자리가 빈 상태다.
-- 라운드를 지우면 뒤 라운드가 seq 를 물려받으므로 유니크에 _active_check 가 필요하다.
CREATE TABLE interview_round (
    id                    BIGINT     NOT NULL AUTO_INCREMENT,
    interview_plan_id     BIGINT     NOT NULL,
    seq                   SMALLINT   NOT NULL,
    interviewee_member_id BINARY(16) NULL,
    interview_minutes     SMALLINT   NOT NULL,
    feedback_minutes      SMALLINT   NOT NULL,
    created_at            DATETIME   NOT NULL,
    updated_at            DATETIME   NOT NULL,
    deleted_at            DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_round_plan_seq_active UNIQUE (interview_plan_id, seq, _active_check)
);

-- 라운드별 면접관·관찰자 배정. 면접자는 라운드의 컬럼이라 여기 들어가지 않는다.
CREATE TABLE round_assignment (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    interview_round_id BIGINT      NOT NULL,
    member_id          BINARY(16)  NOT NULL,
    assignment_role    VARCHAR(20) NOT NULL,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    deleted_at         DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_round_assignment_round_member_active UNIQUE (interview_round_id, member_id, _active_check)
);
CREATE INDEX ix_round_assignment_member_id ON round_assignment (member_id);

-- 구두 피드백 단계의 산출물. 최종 피드백과 자가 피드백을 feedback_type 으로 구분한다.
CREATE TABLE round_feedback (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    interview_round_id BIGINT      NOT NULL,
    author_member_id   BINARY(16)  NOT NULL,
    feedback_type      VARCHAR(20) NOT NULL,
    content            TEXT        NOT NULL,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    deleted_at         DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_round_feedback_round_author_active UNIQUE (interview_round_id, author_member_id, _active_check)
);

-- 질문은 라운드가 아니라 (룸, 대상 면접자)에 매단다 — 라운드 편집을 넘나들며 살아남아야 한다.
-- 꼬리질문은 parent_question_id 자기 참조로 표현한다.
CREATE TABLE question (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    room_id            BINARY(16)  NOT NULL,
    target_member_id   BINARY(16)  NOT NULL,
    author_member_id   BINARY(16)  NOT NULL,
    parent_question_id BIGINT      NULL,
    content            TEXT        NOT NULL,
    source             VARCHAR(20) NOT NULL,
    asked              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         DATETIME    NOT NULL,
    updated_at         DATETIME    NOT NULL,
    deleted_at         DATETIME    NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_question_room_target ON question (room_id, target_member_id);
CREATE INDEX ix_question_parent_question_id ON question (parent_question_id);

-- 질문별 답변 요약. 면접관·관찰자 전원의 요약이 서로 공유된다.
CREATE TABLE answer_summary (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    question_id      BIGINT     NOT NULL,
    author_member_id BINARY(16) NOT NULL,
    content          TEXT       NOT NULL,
    created_at       DATETIME   NOT NULL,
    updated_at       DATETIME   NOT NULL,
    deleted_at       DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_answer_summary_question_author_active UNIQUE (question_id, author_member_id, _active_check)
);

-- 질문별 코멘트. 한 사람이 여러 건 남길 수 있어 유니크를 걸지 않는다.
CREATE TABLE question_comment (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    question_id      BIGINT      NOT NULL,
    author_member_id BINARY(16)  NOT NULL,
    comment_type     VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    deleted_at       DATETIME    NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_question_comment_question_id ON question_comment (question_id);

-- 클로징의 질문 평가(기억에 남아요 / 아쉬워요).
CREATE TABLE question_vote (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    question_id     BIGINT      NOT NULL,
    voter_member_id BINARY(16)  NOT NULL,
    vote            VARCHAR(20) NOT NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    deleted_at      DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_question_vote_question_voter_active UNIQUE (question_id, voter_member_id, _active_check)
);

-- 클로징의 시간 배분 체감(짧았어요 / 적당했어요 / 길었어요). 집계로만 쓴다.
CREATE TABLE closing_response (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_id    BINARY(16)  NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    pacing     VARCHAR(20) NOT NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    deleted_at DATETIME    NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_closing_response_room_member_active UNIQUE (room_id, member_id, _active_check)
);

-- 출석 기록. 완료 처리 시작 시 전원 출석으로 표시하고 예외만 지각·노쇼로 바꾼다.
-- 신뢰 통계의 분모라 물리 삭제하지 않는다.
CREATE TABLE attendance (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    room_id            BINARY(16)   NOT NULL,
    member_id          BINARY(16)   NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    change_reason      VARCHAR(200) NULL,
    recorder_member_id BINARY(16)   NOT NULL,
    recorded_at        DATETIME     NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    deleted_at         DATETIME     NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_attendance_room_member_active UNIQUE (room_id, member_id, _active_check)
);
CREATE INDEX ix_attendance_member_id ON attendance (member_id);

-- ── 신뢰 ────────────────────────────────────────────────────────────────
-- 참여자 상호 평가. 신뢰 정보는 별도 테이블 없이 attendance 와 review 의 집계로 만든다.
-- visible_at = 제출 + 3시간. hidden_at 은 운영이 가린 것, deleted_at 은 작성자가 거둔 것이다.
CREATE TABLE review (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    room_id          BINARY(16) NOT NULL,
    author_member_id BINARY(16) NOT NULL,
    target_member_id BINARY(16) NOT NULL,
    rating           SMALLINT   NOT NULL,
    content          TEXT       NULL,
    meet_again       BOOLEAN    NULL,
    visible_at       DATETIME   NOT NULL,
    hidden_at        DATETIME   NULL,
    reported_at      DATETIME   NULL,
    created_at       DATETIME   NOT NULL,
    updated_at       DATETIME   NOT NULL,
    deleted_at       DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_review_room_author_target_active UNIQUE (room_id, author_member_id, target_member_id, _active_check)
);
CREATE INDEX ix_review_target_member_id ON review (target_member_id);

-- 평가 태그(다건). 열거값이며 마스터 테이블을 두지 않는다. 값 컬렉션이라 타임스탬프도 없다.
CREATE TABLE review_tag (
    review_id BIGINT      NOT NULL,
    tag       VARCHAR(40) NOT NULL,
    CONSTRAINT uk_review_tag UNIQUE (review_id, tag)
);

-- ── 채팅 ────────────────────────────────────────────────────────────────
-- 룸당 1개. 룸 생성 시 자동으로 만들어진다. 읽기 전용 전환은 read_only_at 이 갖는다.
CREATE TABLE chat_room (
    id           BIGINT     NOT NULL AUTO_INCREMENT,
    room_id      BINARY(16) NOT NULL,
    read_only_at DATETIME   NULL,
    created_at   DATETIME   NOT NULL,
    updated_at   DATETIME   NOT NULL,
    deleted_at   DATETIME   NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_room_active UNIQUE (room_id, _active_check)
);

-- 텍스트 메시지만 지원하고 사용자에게 수정·삭제를 제공하지 않는다.
-- deleted_at 은 운영이 신고된 메시지를 가리기 위한 것이다(없으면 DB 직접 삭제밖에 방법이 없다).
-- author_member_id 가 NULL 이면 시스템 메시지(입장·퇴장 알림)다.
CREATE TABLE chat_message (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    chat_room_id     BIGINT      NOT NULL,
    author_member_id BINARY(16)  NULL,
    message_type     VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    deleted_at       DATETIME    NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_chat_message_chat_room_id ON chat_message (chat_room_id);
