-- 시각 컬럼을 DATETIME(6) 으로 올린다(MOI-428).
-- 초 단위로는 같은 초에 일어난 두 사건의 선후를 가릴 수 없다. 확정 참여자 판정이
-- participation.left_at 과 room_status_log.occurred_at 의 대소로 갈리므로, 확정 직후 같은 초에
-- 나간 사람이 "확정 전에 나간 사람"으로 뒤집힌다. 부등호로는 못 푼다 - DB 에 순서 정보가 없다.
--
-- 비교에 쓰는 컬럼만 고르지 않고 전부 올린다. "이 컬럼은 비교에 안 쓴다"는 가정은 다음 기능에서 깨진다.
-- 이미 초 단위로 쌓인 값의 선후는 복원되지 않는다. 소수부가 .000000 으로 채워질 뿐이다.
--
-- ⚠️ 크롤러 소유 테이블(sido, sigungu, job_group, job_role, company, job_posting)은 DEFAULT,
--    ON UPDATE CURRENT_TIMESTAMP, COMMENT 를 그대로 다시 적는다. MODIFY COLUMN 은 컬럼 정의를
--    통째로 갈아치우므로 타입만 적으면 그 셋이 조용히 사라진다. 크롤러 적재 파이프라인이
--    ON UPDATE 에 의존한다(schema.sql 머리말의 "의도적으로 어긋나는 부분" 1번).
--    기본값의 fsp 도 컬럼과 같아야 한다 - CURRENT_TIMESTAMP 가 아니라 CURRENT_TIMESTAMP(6).
--
-- resume 는 이미 DATETIME(6) 이라 대상이 아니다. job_posting_role, review_tag 는 시각 컬럼이 없다.

-- ── 크롤러 소유(읽기 전용 참조 데이터) ──────────────────────────────────

ALTER TABLE sido
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성시각',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정시각',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)';

ALTER TABLE sigungu
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성시각',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정시각',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)';

ALTER TABLE job_group
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성시각',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정시각',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)';

ALTER TABLE job_role
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성시각',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정시각',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)';

ALTER TABLE company
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성시각',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정시각',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '재수집 시 폐기(retire)된 시각. NULL=유효 현행, 값 존재=폐기(구버전)';

-- 데이터가 가장 많은 테이블이다(크롤링 원본 + mediumtext content + FULLTEXT ft_content).
-- 정밀도 변경은 테이블 재빌드라 dev 에서 시간이 걸릴 수 있다. Flyway 전용 DataSource 가
-- socketTimeout=600000 으로 붙으므로 앱 DataSource 의 3초 제한에는 걸리지 않는다(db-core.yml).
ALTER TABLE job_posting
    MODIFY COLUMN posted_at DATETIME(6) NULL COMMENT '공고 게시일시(원본 created_at)',
    MODIFY COLUMN end_date DATETIME(6) NULL COMMENT '마감일시(상시=NULL)',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성시각',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정시각',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '재수집 시 폐기(retire)된 시각(NULL=유효 현행, 값=폐기 구버전)';

-- ── 회원 ────────────────────────────────────────────────────────────────

ALTER TABLE member
    MODIFY COLUMN last_login_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE social_account
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE refresh_token
    MODIFY COLUMN expires_at DATETIME(6) NOT NULL,
    MODIFY COLUMN revoked_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE web_push_subscription
    MODIFY COLUMN registered_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE member_profile
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE member_profile_interest_company
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE member_profile_interest_job_role
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE terms
    MODIFY COLUMN effective_from DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE terms_agreement
    MODIFY COLUMN agreed_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

-- ── 룸 ──────────────────────────────────────────────────────────────────

ALTER TABLE room
    MODIFY COLUMN start_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE room_status_log
    MODIFY COLUMN occurred_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE room_application
    MODIFY COLUMN applied_at DATETIME(6) NOT NULL,
    MODIFY COLUMN handled_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE participation
    MODIFY COLUMN joined_at DATETIME(6) NOT NULL,
    MODIFY COLUMN left_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE resume_submission
    MODIFY COLUMN submitted_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

-- ── 면접 진행 ───────────────────────────────────────────────────────────

ALTER TABLE interview_plan
    MODIFY COLUMN last_assigned_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE interview_round
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE round_assignment
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE round_feedback
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE question
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE answer_summary
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE question_comment
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE question_vote
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE closing_response
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE attendance
    MODIFY COLUMN recorded_at DATETIME(6) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

-- ── 신뢰·채팅·스캐폴딩 ──────────────────────────────────────────────────

ALTER TABLE review
    MODIFY COLUMN visible_at DATETIME(6) NOT NULL,
    MODIFY COLUMN hidden_at DATETIME(6) NULL,
    MODIFY COLUMN reported_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE chat_room
    MODIFY COLUMN read_only_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE chat_message
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    MODIFY COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE outbox
    MODIFY COLUMN lease_until DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

ALTER TABLE example_entity
    MODIFY COLUMN deleted_at DATETIME(6) NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
