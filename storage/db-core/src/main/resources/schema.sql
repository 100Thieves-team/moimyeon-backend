-- moimyeon core 스키마 (단일 소스)
--
-- - 엔티티는 매핑만 담당하고, 테이블/타입/제약/인덱스는 이 파일에서 관리한다(유니크만 엔티티에도 선언).
-- - 적용: 로컬/테스트(H2)는 spring.sql.init 으로 실행, 로컬 MySQL 은 docker-compose 의 initdb 로 로드.
--   dev/live 같은 영속 DB 는 Flyway 마이그레이션(db/migration/)이 반영한다 — 이 파일을 직접 돌리지 않는다.
--   이 파일은 "전체 스키마의 현재 모습", 마이그레이션은 "거기까지 간 경로"다. 둘은 같은 결과여야 한다.
-- - H2(MODE=MySQL)와 MySQL 8 모두에서 도는 공통 문법만 사용한다(ENGINE/CHARSET 등 방언 지시 없음).
-- - FK 제약은 걸지 않는다. 참조 무결성은 애플리케이션의 저장 전 명시 검증이 담당한다
--   (docs/conventions/storage.md). 테이블 간 참조는 *_id 컬럼으로만 표현한다.
-- - CHECK 제약도 걸지 않는다. 값 범위·조건부 필수(예: 오프라인 룸의 지역, 후기의 작성자≠대상자)는
--   같은 이유로 애플리케이션이 검증한다.
-- - 인덱스도 이 파일에서 관리한다. 엔티티에는 적지 않는다.
--   FK 제약을 걸지 않으므로 InnoDB 가 자동 생성해 주는 인덱스가 없다 — 참조 컬럼은 직접 걸어야 한다.
--   거는 기준은 "부모에서 자식 목록을 찾는 경로"다(내 이력서 목록, 내 참여 룸, 받은 후기 …).
--   감사용 컬럼(handler_·recorder_·author_member_id 등)은 그 경로로 조회하지 않아 걸지 않는다.
--   유니크 제약이 이미 인덱스이므로 그 선두 컬럼에는 따로 걸지 않는다 —
--   예로 participation 은 (room_id, member_id) 유니크가 room_id 를 덮으므로 member_id 만 건다.
--   그 밖의 성능 인덱스는 실측으로 느린 쿼리가 확인된 뒤 근거와 함께 추가한다.
-- - 재실행 안전을 위해 DROP IF EXISTS 후 CREATE. DROP 은 인메모리 H2 재초기화·MySQL 최초
--   initdb 용이다. 데이터가 있는 영속 DB 에 이 파일 전체를 수동 실행하면 파괴적이므로 금지 —
--   그쪽 변경은 db/migration/ 에 마이그레이션을 추가하는 것으로만 한다.
--
-- ⚠️ 크롤러 소유 테이블은 dev/live 에서 이 파일과 의도적으로 어긋나는 부분이 셋 있다.
--   크롤러 적재 파이프라인이 의존하는 것이라 마이그레이션이 건드리지 않는다.
--     1. updated_at 의 ON UPDATE CURRENT_TIMESTAMP — H2 가 지원하지 않아 여기 적을 수 없다.
--     2. 크롤러 테이블 사이의 FK 제약(fk_sigungu_sido, fk_job_role_group, fk_posting_company,
--        fk_ppr_posting/fk_ppr_role) — 특히 job_posting_role 의 ON DELETE CASCADE 는
--        크롤러가 공고를 지울 때 매핑을 함께 정리하는 수단이라 제거하면 고아 행이 남는다.
--     3. job_posting.content 의 FULLTEXT 인덱스(ft_content) — H2 에 대응 문법이 없다.
--   그 밖(컬럼명·유니크명·인덱스명·타입)은 이 파일이 정본이고 dev/live 도 여기에 맞춘다.
--
-- 식별자 정책
-- - 외부에 노출되는 식별자와 분산 전달 식별자는 BINARY(16) UUID:
--   member, resume, room, terms, terms_agreement, outbox.
--   UUID 는 시간 정렬(UUIDv7)로 생성한다 — 무작위 v4 는 InnoDB 클러스터 인덱스를 파편화시킨다.
-- - 그 밖에는 BIGINT AUTO_INCREMENT.
-- - PK 컬럼명은 테이블 안에서 항상 id, 참조 컬럼명은 <테이블>_id.
--
-- 소프트 삭제 정책 — deleted_at 컬럼의 유무가 곧 엔티티의 베이스 상속 여부다
-- - 기본은 소프트 삭제다. deleted_at DATETIME(6) NULL 을 두고 엔티티는 BaseEntity/UuidBaseEntity 를
--   상속한다. 물리 삭제 대신 delete(now) 로 처리하고, 조회 제외는 각 Finder 의 파생 쿼리가 담당한다.
--   운영에서 데이터를 되살려야 하는 일이 생각보다 잦다 — 잘못 지운 것, 신고로 내렸다가 복원하는 것,
--   분쟁이 나서 지워진 내용을 확인해야 하는 것. 지워진 행이 남아 있으면 전부 SQL 한 줄로 끝난다.
-- - 별도 라이프사이클이 있는 엔티티는 상속하지 않는다. 업무 데이터가 아니거나 별도 무효화 수단이
--   있어 deleted_at 보다 정확하기 때문이다. 대표 사례는 다음과 같다.
--     social_account — 재가입 차단이 (provider, provider_id) 유니크 점유에 의존한다.
--                      지운 표식을 남기면 그 점유가 풀려 차단이 뚫린다.
--     refresh_token  — 삭제가 아니라 무효화이며 revoked_at 이 그 시각을 갖는다.
--     outbox — 외부 전달 전까지의 내구성 작업 기록이며 전달 생명주기와 보존 정책을 따른다.
--   각 테이블의 사유는 아래 테이블 주석에 적어 두었다. 엔티티를 만들 때 그 문장을 파일 상단으로 옮긴다 —
--   어느 쪽인지의 원본은 문서가 아니라 각 엔티티 파일이다(docs/conventions/storage.md).
-- - 값 컬렉션(@ElementCollection)은 엔티티가 아니므로 id·타임스탬프·deleted_at 을 두지 않는다.
--   부모 후기가 지워지면 함께 지워진다: review_tag.
--   값 컬렉션으로 두는 기준은 "마스터 테이블이 없는 단순 값인가"다. 양쪽이 다 엔티티인 M:N 조인은
--   조인 테이블도 엔티티로 둔다(member_profile_interest_company·member_profile_interest_job_role) —
--   값 컬렉션은 한 건만 바뀌어도 소유자의 행을 전량 DELETE 후 재삽입하고, 레포지터리가 없어
--   "이 직무에 관심 있는 회원" 같은 조회를 못 하며, deleted_at 이 없어 소프트 삭제 원칙에서도 빠진다.
-- - 참조 데이터의 deleted_at 은 뜻이 하나 더 있다 — 재크롤에서 사라진 소스의 폐기 표식(retire)이다.
--   크롤러 ERD 의 retired_at 이 이 이름으로 통일되어 있다. 뜻은 같다: NULL = 유효 현행.
--
-- 소프트 삭제와 유니크 제약 — _active_check 생성 컬럼
-- - 유니크 인덱스는 NULL 을 서로 다른 값으로 취급한다. deleted_at 을 유니크에 그대로 넣으면
--   방향이 반대가 된다 — 살아있는 행(deleted_at IS NULL)끼리 중복이 통과해 버린다.
--   그래서 살아있을 때만 값이 있는 컬럼을 따로 만들어 유니크에 넣는다.
--
--     _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
--     CONSTRAINT uk_xxx_active UNIQUE (a, b, _active_check)
--
--   STORED/VIRTUAL 키워드는 H2 가 파싱하지 못하므로 생략한다(MySQL 기본값이 VIRTUAL).
--   IF() 대신 CASE WHEN 을 쓰는 것도 같은 이유다. PK 에는 통하지 않는다 —
--   PK 는 NULL 을 허용하지 않아 이 트릭 자체가 성립하지 않으므로, 그때는 되살리기로 처리한다.
-- - 어느 유니크에 붙일지는 그 제약이 무엇을 뜻하는가로 가른다.
--     "동시에 하나만" → _active_check 를 붙인다. 지워진 뒤 같은 키로 다시 만들 수 있어야 한다.
--                       (참가 신청·참여·출석·후기·라운드·배정·투표 등 트랜잭션 테이블 전부)
--     "영원히 하나만" → 전체 행을 대상으로 그대로 둔다.
--                       member.nickname 은 탈퇴자 것도 재사용하지 않는다.
--                       참조 데이터의 업무키(sido_code·code·corp_code·source_uid 등)는 재크롤 때
--                       지워진 행을 찾아 되살려야 하므로 유니크가 전체를 덮어야 upsert 가 성립한다.
--                       terms 의 (type, version) 도 같은 버전을 다시 발행하지 않는다.
-- - member_profile 은 uk_member_profile_member 에 _active_check 를 붙이지 않는다 — 소프트 삭제된
--   프로필도 유니크를 점유해야 재작성이 새 행이 아니라 되살리기가 된다(프로필은 회원당 하나뿐이다).
--
-- 시각 컬럼은 예외 없이 DATETIME(6) 이다 — 초 단위로는 같은 초에 일어난 두 사건의 선후를 못 가린다.
--   확정 참여자 판정이 실제로 그 대소로 갈린다(participation.left_at vs room_status_log.occurred_at).
--   비교에 안 쓰는 컬럼만 골라 올리면 그 가정이 다음 기능에서 깨지므로 전부 올린다(MOI-428).
--   기본값도 CURRENT_TIMESTAMP(6) 으로 같이 올린다 — MySQL 은 컬럼과 기본값의 fsp 가 다르면 거부한다.
--
-- created_at/updated_at 은 모든 엔티티 테이블이 갖는다(값 컬렉션인 review_tag 만 제외).
--   참조 데이터는 갱신 주체인 크롤러가 직접 세팅한다 —
--   ON UPDATE CURRENT_TIMESTAMP 는 H2 가 지원하지 않아 이 파일에 쓰지 않는다(위 ⚠️ 참고).
--
-- 설계 근거는 docs/design/erd/ 참고 (개념 Step 1~8 + 논리모델링.md).
-- 코딩 규약은 docs/conventions/storage.md 참고.

DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS outbox;
DROP TABLE IF EXISTS chat_room;
DROP TABLE IF EXISTS review_skip;
DROP TABLE IF EXISTS review_tag;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS closing_response;
DROP TABLE IF EXISTS question_vote;
DROP TABLE IF EXISTS question_comment;
DROP TABLE IF EXISTS answer_summary;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS round_feedback;
DROP TABLE IF EXISTS round_assignment;
DROP TABLE IF EXISTS interview_round;
DROP TABLE IF EXISTS interview_plan;
DROP TABLE IF EXISTS resume_submission;
DROP TABLE IF EXISTS participation;
DROP TABLE IF EXISTS room_application;
DROP TABLE IF EXISTS room_status_log;
DROP TABLE IF EXISTS room;
DROP TABLE IF EXISTS resume;
DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS web_push_subscription;
DROP TABLE IF EXISTS terms_agreement;
DROP TABLE IF EXISTS member_profile_interest_job_role;
DROP TABLE IF EXISTS member_profile_interest_company;
DROP TABLE IF EXISTS member_profile;
DROP TABLE IF EXISTS social_account;
DROP TABLE IF EXISTS terms;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS job_posting_role;
DROP TABLE IF EXISTS job_posting;
DROP TABLE IF EXISTS job_role;
DROP TABLE IF EXISTS job_group;
DROP TABLE IF EXISTS sigungu;
DROP TABLE IF EXISTS sido;
DROP TABLE IF EXISTS company;
DROP TABLE IF EXISTS example_entity;

-- ── 참조 데이터(크롤러 소유) ─────────────────────────────────────────────
-- deleted_at: 재크롤 미출현 시 크롤러가 세팅(NULL=유효 현행, 값 존재=폐기된 구버전).
-- 업무키에 유니크를 걸어 재적재 멱등성을 보장한다: sido_code, sigungu_code, code, corp_code, source_uid.
-- 유니크가 전체 행을 덮어야 크롤러가 폐기된 행을 찾아 되살리는 upsert 가 성립하므로 _active_check 를 쓰지 않는다.
--
-- ⚠️ 앱의 읽기 전용 원칙에 예외가 둘 있다 — company 와 job_posting.
--   카탈로그에 없는 회사·공고는 사용자가 룸을 만들며 즉시 등록해야 하므로 앱이 INSERT 한다.
--   대신 앱이 만든 행은 verified = FALSE 로만 들어가고, TRUE 로 올리는 것과 갱신·병합·폐기는
--   여전히 크롤러·운영의 몫이다. 나머지 테이블(sido·sigungu·job_group·job_role·job_posting_role)은
--   읽기 전용 그대로다.

-- 지역-시도 마스터(법정동 2026) — 16개(전남광주통합특별시 포함)
CREATE TABLE sido (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    sido_code  CHAR(2)     NOT NULL,
    name       VARCHAR(20) NOT NULL,
    short_name VARCHAR(10) NOT NULL,
    is_metro   BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order SMALLINT    NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sido_sido_code UNIQUE (sido_code),
    CONSTRAINT uk_sido_name UNIQUE (name),
    CONSTRAINT uk_sido_short_name UNIQUE (short_name)
);

-- 지역-시군구 마스터(법정동 2026)
-- name 은 시도 접두 없이는 유일하지 않다(예: 여러 시도의 '중구') — 유니크는 (sido_id, name).
-- level: SI/GUN/GU. 크롤러 ERD 는 MySQL ENUM 이지만 여기서는 VARCHAR 로 둔다 —
--   H2 와 공통 문법이어야 하고, 엔티티가 @Enumerated(STRING) 으로 매핑한다.
CREATE TABLE sigungu (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    sido_id      BIGINT      NOT NULL,
    sigungu_code CHAR(5)     NULL,
    name         VARCHAR(40) NOT NULL,
    level        VARCHAR(10) NOT NULL,
    sort_order   SMALLINT    NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at   DATETIME(6) NULL,
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
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at   DATETIME(6) NULL,
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
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_job_role_code UNIQUE (code)
);
CREATE INDEX ix_job_role_job_group_id ON job_role (job_group_id);

-- 회사 (corp_code = DART 고유번호, 식별 앵커)
-- 크롤러가 채우는 것이 기본이지만, 카탈로그에 없는 회사는 사용자가 룸을 만들며 즉시 등록할 수 있다.
-- 그때는 corp_code 가 비고 verified = FALSE 로 들어간다. 운영이 사후 검증하거나 중복이면 병합한다.
-- 미검증 회사는 탐색 필터의 선택 목록에서만 숨기고, 그 회사의 공고로 만들어진 룸은 정상 노출한다.
-- verified 기본값은 TRUE 다(job_posting 과 다르다). 컬럼을 생략하는 크롤러 적재분이 검색에서
-- 사라지지 않게 하려는 것이고, 앱이 만드는 행은 JPA 가 값을 명시하므로 여전히 FALSE 다.
CREATE TABLE company (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    corp_code            CHAR(8)      NULL,
    name_kr              VARCHAR(200) NOT NULL,
    name_normalized      VARCHAR(200) NULL,
    verified             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by_member_id BINARY(16)   NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at           DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_corp_code UNIQUE (corp_code)
);
CREATE INDEX ix_company_name_normalized ON company (name_normalized);

-- 채용공고(크롤러 원본 = 직행 2026+). 룸이 참조하는 공고가 바로 이 테이블이다.
-- source_uid 가 업무키다 — 크롤러의 재적재 멱등성이 이 유니크에 달려 있다.
--   사용자가 링크로 즉시 등록한 공고도 같은 테이블에 들어가며, 그때는 앱이 source_uid 를 발급한다
--   (크롤러가 쓰는 출처 UUID 와 섞이지 않도록 앱 전용 접두를 붙인다).
-- company_id 는 NULL 을 허용한다 — 크롤러가 이름키로 회사를 유일매칭하지 못한 공고가 있다.
-- 공고의 라이프사이클은 is_open/end_date 가 담당하고, deleted_at 은 재수집에서 사라진 폐기 표식이다.
-- regions/employee_types/educations 는 '|' 구분 다중값 비정규화 컬럼이다(크롤러 ERD 결정).
-- content 는 dev/live 에서 MEDIUMTEXT + FULLTEXT 인덱스(ft_content)다 —
--   H2 에 대응 문법이 없어 여기서는 TEXT 로만 둔다. 본문 전문검색은 MySQL 환경에서만 동작한다.
CREATE TABLE job_posting (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    source_uid           VARCHAR(64)  NOT NULL,
    company_id           BIGINT       NULL,
    title                VARCHAR(300) NOT NULL,
    regions              VARCHAR(100) NULL,
    career_min           SMALLINT     NULL,
    career_max           SMALLINT     NULL,
    employee_types       VARCHAR(60)  NULL,
    educations           VARCHAR(60)  NULL,
    posted_at            DATETIME(6)  NULL,
    end_date             DATETIME(6)  NULL,
    deadline_type        VARCHAR(20)  NULL,
    is_open              BOOLEAN      NULL,
    source_url           VARCHAR(400) NULL,
    redirect_url         VARCHAR(400) NULL,
    summary              TEXT         NULL,
    content              TEXT         NULL,
    verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_member_id BINARY(16)   NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at           DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_job_posting_source_uid UNIQUE (source_uid)
);
CREATE INDEX ix_job_posting_company_id ON job_posting (company_id);
CREATE INDEX ix_job_posting_is_open ON job_posting (is_open);

-- 공고↔직무 매핑(크롤러의 job_cat2 정규화). 한 공고가 여러 직무에 걸린다.
-- 값 컬렉션이 아니라 크롤러가 소유하는 매핑 테이블이라 id 를 갖는다(크롤러 ERD 결정).
-- 타임스탬프·deleted_at 은 두지 않는다 — 공고가 폐기되면 매핑도 함께 정리된다.
CREATE TABLE job_posting_role (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT NOT NULL,
    job_role_id    BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_job_posting_role_posting_role UNIQUE (job_posting_id, job_role_id)
);
CREATE INDEX ix_job_posting_role_job_role_id ON job_posting_role (job_role_id);

-- ── 회원 ────────────────────────────────────────────────────────────────

-- nickname 은 가입 시 서버가 자동 생성하는 표시 이름(회원 신원의 일부, 전역 유일)
-- email 에는 유니크를 걸지 않는다 — 같은 이메일이어도 소셜 식별자가 다르면 별도 계정이다.
-- 탈퇴는 deleted_at 으로 표현한다. status 는 그와 직교하는 제재 상태(ACTIVE/RESTRICTED)만 담는다.
-- 닉네임 유니크는 탈퇴자를 포함한 전체 회원 대상이라 _active_check 를 쓰지 않는다 —
--   떠난 사람의 이름이 곧바로 다른 사람에게 넘어가면 지난 룸의 기록을 오해하게 된다.
CREATE TABLE member (
    id            BINARY(16)   NOT NULL,
    email         VARCHAR(320) NOT NULL,
    nickname      VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    last_login_at DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
);

-- 베이스 미상속: 재가입 차단이 (provider, provider_id) 유니크 점유에 의존한다.
--   지운 표식을 남기고 _active_check 를 붙이면 그 점유가 풀려 차단이 뚫린다.
--   회원과 함께 생성·삭제되는 유일한 연관관계이기도 하다(cascade + orphanRemoval).
CREATE TABLE social_account (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    provider     VARCHAR(20)  NOT NULL,
    provider_id  VARCHAR(100) NOT NULL,
    linked_email VARCHAR(320) NULL,
    member_id    BINARY(16)   NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_account_provider_provider_id UNIQUE (provider, provider_id)
);
CREATE INDEX ix_social_account_member_id ON social_account (member_id);

-- 베이스 미상속: 삭제가 아니라 무효화이며 revoked_at 이 그 시각을 갖는다.
CREATE TABLE refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_member_id ON refresh_token (member_id);
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);

-- 베이스 미상속: 브라우저의 현재 푸시 전달 경로다. 해지는 복원할 업무 삭제가 아니라
-- 더 이상 사용하지 않을 외부 등록 식별자의 제거이므로 물리 삭제한다.
-- 긴 등록 식별자 전체를 인덱싱하지 않고 SHA-256 해시로 동일 브라우저 등록을 식별한다.
CREATE TABLE web_push_subscription (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    member_id         BINARY(16)  NOT NULL,
    registration      TEXT        NOT NULL,
    registration_hash CHAR(64)    NOT NULL,
    registered_at     DATETIME(6) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_web_push_subscription_registration_hash UNIQUE (registration_hash)
);
CREATE INDEX ix_web_push_subscription_member_id ON web_push_subscription (member_id);

-- 소개 정보(전부 선택 입력). 행 존재 = 온보딩(최초 소개 작성) 제출 완료라는 파생 사실의 근거.
-- 관심 회사·관심 직무는 다건이라 별도 조인 엔티티로 뺐다(아래) — 값 컬렉션이 아니다.
-- 베이스 상속(AbstractEntity) — PK 가 member_id 라 UuidBaseEntity 는 쓸 수 없다.
--   PK 에는 _active_check 트릭이 통하지 않으므로(PK 는 NULL 불가) 재작성은 되살리기로 처리한다.
--   온보딩 완료 판정은 "행 존재"에서 "살아있는 행 존재"로 읽는다.
-- 직무 단일 참조(job_role_id) 컬럼은 두지 않는다 — 관심 직무는 다건이라
--   member_profile_interest_job_role 로 뺐다.
CREATE TABLE member_profile (
    id         BINARY(16)   NOT NULL,
    member_id  BINARY(16)   NOT NULL,
    bio        VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_profile_member UNIQUE (member_id)
);

-- 관심 회사(프로필↔company 의 M:N 조인). 양쪽이 다 엔티티이므로 값 컬렉션이 아니라 엔티티다 —
-- id·타임스탬프·deleted_at 을 갖는다. 값 컬렉션은 마스터가 없는 단순 값에만 쓴다(review_tag).
-- 관심에서 뺀 것은 소프트 삭제로 남긴다. _active_check 덕분에 뺐다가 다시 담으면 새 행으로 들어가고,
--   "언제부터 관심이었는가"가 created_at 에 보존된다.
CREATE TABLE member_profile_interest_company (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    profile_id    BINARY(16) NOT NULL,
    company_id    BIGINT     NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_member_profile_interest_company_active UNIQUE (profile_id, company_id, _active_check)
);
CREATE INDEX ix_member_profile_interest_company_company_id ON member_profile_interest_company (company_id);

-- 관심 직무(프로필↔job_role 의 M:N 조인). 한 사람이 백엔드와 데이터 엔지니어를 함께 준비하는 일이 흔하다.
-- 프로필의 직무가 아니라 관심 직무이므로 단일 참조가 아니다. 관심 회사와 같은 엔티티 패턴.
CREATE TABLE member_profile_interest_job_role (
    id            BIGINT     NOT NULL AUTO_INCREMENT,
    profile_id    BINARY(16) NOT NULL,
    job_role_id   BIGINT     NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_member_profile_interest_job_role_active UNIQUE (profile_id, job_role_id, _active_check)
);
CREATE INDEX ix_member_profile_interest_job_role_job_role_id ON member_profile_interest_job_role (job_role_id);

-- 회원이 보관하는 이력서. 등록 후 내용을 수정하지 않는다 — 바꾸려면 새 이력서를 등록한다.
-- 이미 제출한 룸의 참여자가 본 이력서가 그대로 보존되어야 하기 때문이다.
-- summary_content: 등록 시 1회 생성해 여러 룸의 신청에 재사용한다.
-- 베이스 상속: 사용자의 삭제는 deleted_at 으로 관리하며 행과 파일은 즉시 물리 삭제하지 않는다.
--   이미 제출한 내용의 독립적인 보존은 resume_submission 의 제출 시점 사본이 담당해야 한다.
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

-- status = DEPRECATED 는 "구버전이라 신규 동의 대상 아님"(기존 동의는 유효), deleted_at 은 행 자체의 폐기.
-- (type, version) 유니크는 전체 대상이다 — 같은 버전을 다시 발행하지 않는다.
CREATE TABLE terms (
    id             BINARY(16)   NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    version        VARCHAR(20)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    required       BOOLEAN      NOT NULL,
    effective_from DATETIME(6)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_type_version UNIQUE (type, version)
);

-- 동의 이력은 append-only: 이미 쌓인 사실(누가 언제 무엇에 동의했는가)을 고치거나 물리 삭제하지 않는다.
-- 유일한 예외가 deleted_at 이다. 잘못 쌓인 행을 가리는 소프트 삭제만 허용하고, 그것도 값의 수정이 아니라
-- "이 기록은 무효"라는 표식의 추가로 읽는다. 나머지 컬럼은 한 번 쓰면 바뀌지 않는다.
-- 재동의는 되살리기가 아니라 새 행으로 append 한다 — 언제 다시 동의했는지가 증빙의 핵심이다.
-- _active_check 가 그 append 를 가능하게 한다(유니크를 살아있는 행끼리만 걸어 준다).
CREATE TABLE terms_agreement (
    id            BINARY(16) NOT NULL,
    member_id     BINARY(16) NOT NULL,
    terms_id      BINARY(16) NOT NULL,
    agreed_at     DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_agreement_member_terms_active UNIQUE (member_id, terms_id, _active_check)
);
CREATE INDEX ix_terms_agreement_member_id ON terms_agreement (member_id);

-- ── 룸(모의면접) ────────────────────────────────────────────────────────
-- "세션"은 기술 용어와 겹쳐 room 으로 부른다. 사용자 화면에는 "면접"으로 표시한다.

-- 룸에 방장 컬럼을 두지 않는다 — participation.participation_role = 'HOST' 가 유일한 진실 원천이다.
--   룸에도 두면 위임할 때마다 두 곳을 맞춰야 하고 그 동기화 규칙이 코드 곳곳에 퍼진다.
-- 회사 컬럼도 두지 않는다 — job_posting 이 필수이므로 회사는 공고를 통해 알 수 있다.
--   중복 보관하면 룸의 회사와 공고의 회사가 어긋나는 모순을 막을 수 없다.
-- 확정자·완료자와 그 시각은 room_status_log 에 남긴다.
-- 정원 마감은 저장하지 않는다 — 현재 참여 인원 = max_capacity 로 계산한다.
-- sigungu_id: 오프라인 룸에서만 채운다(온라인이면 NULL). 조건부 필수는 애플리케이션이 검증한다.
-- job_role_id: 공고가 여러 직무에 걸릴 수 있으므로(job_posting_role) 룸이 그중 하나를 고른다.
-- 베이스 상속: status 의 CANCELED 는 방장이 모집을 접은 것이고, deleted_at 은 운영이 룸을 내린 것이다.
--   둘을 한 컬럼에 섞으면 "누가 왜 없앴는가"를 되물을 수 없다. 스팸·부적절한 룸을 내렸다가
--   이의 제기로 되살리는 일이 실제로 생기므로 지운 흔적이 남아야 한다.
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
    start_at         DATETIME(6)  NOT NULL,
    duration_minutes SMALLINT     NOT NULL,
    resume_public    BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    deleted_at       DATETIME(6)  NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_room_job_posting_id ON room (job_posting_id);
CREATE INDEX ix_room_job_role_id ON room (job_role_id);
CREATE INDEX ix_room_sigungu_id ON room (sigungu_id);
-- 탐색 목록의 정렬 2종(일정 빠른 순·최근 생성순). 정렬 키 뒤의 id 는 커서 비교가 (정렬 키, id) 복합이라
-- 필요하다 — 없으면 동점 구간마다 정렬이 다시 붙는다.
CREATE INDEX ix_room_status_start_at_id ON room (status, start_at, id);
CREATE INDEX ix_room_status_created_at_id ON room (status, created_at, id);

-- 룸의 상태를 바꾼 사람과 시각. 비즈니스 로직이 읽는 값이 아니라 분쟁 대응·지표용이다.
-- 방장이 위임되면 확정자와 완료자가 달라질 수 있어 룸의 컬럼으로 두지 않았다.
-- (room_id, transition_type) 유니크가 확정·완료 처리의 멱등성을 DB 레벨에서 보장한다.
-- append-only 로 다루되 베이스는 상속한다 — 쌓인 전이 기록은 고치지 않고,
--   deleted_at 만 예외로 운영이 잘못 찍힌 전이를 걷어낼 때 쓴다. 룸이 소프트 삭제돼도 로그는 남는다.
--   유니크가 멱등성을 보장하므로 걷어낸 뒤 다시 전이할 수 있도록 _active_check 를 붙였다.
CREATE TABLE room_status_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    room_id           BINARY(16)  NOT NULL,
    transition_type   VARCHAR(20) NOT NULL,
    handler_member_id BINARY(16)  NOT NULL,
    occurred_at       DATETIME(6) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted_at        DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_status_log_room_transition_active UNIQUE (room_id, transition_type, _active_check)
);

-- 참가 신청. 철회·자진 취소 후 재신청이 허용되므로 (room_id, applicant_member_id) 에는 유니크를 걸 수 없다.
-- 대신 "대기 상태인 신청은 룸당 회원당 1건"을 pending_member_id 로 표현한다:
--   상태가 대기면 신청자 ID 를, 그 밖에는 NULL 을 애플리케이션이 채운다.
--   유니크 인덱스가 NULL 을 중복 허용하므로 처리된 신청끼리는 충돌하지 않는다.
--   (부분 유니크 인덱스는 MySQL 이 지원하지 않고, 생성 컬럼은 H2 와 문법이 갈려 명시 컬럼으로 둔다.)
-- 베이스 상속: status 의 철회·거절은 신청의 결말이고, deleted_at 은 운영이 스팸 신청을 걷어낸 것이다.
--   _active_check 가 붙어 있어 걷어낸 뒤 곧바로 재신청할 수 있다.
CREATE TABLE room_application (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    room_id             BINARY(16)  NOT NULL,
    applicant_member_id BINARY(16)  NOT NULL,
    pending_member_id   BINARY(16)  NULL,
    note                TEXT        NOT NULL,
    status              VARCHAR(20) NOT NULL,
    reject_reason       VARCHAR(50) NULL,
    handler_member_id   BINARY(16)  NULL,
    applied_at          DATETIME(6) NOT NULL,
    handled_at          DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted_at          DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_application_room_pending_active UNIQUE (room_id, pending_member_id, _active_check)
);
CREATE INDEX ix_room_application_applicant_member_id ON room_application (applicant_member_id);

-- 수락되어 명단에 들어간 상태. 룸 생성 시 방장의 참여가 함께 만들어진다(같은 트랜잭션).
-- 베이스 상속: 나감·제외는 left_at 과 left_by_member_id 가 갖는 정상 흐름이고,
--   deleted_at 은 잘못 만들어진 참여를 운영이 걷어낸 표식이다. 명단은 룸의 정원·통계와 직결되므로
--   물리 삭제하면 무엇이 어긋났는지 되짚을 수 없다.
--   유니크는 JOINED 인 행에만 걸린다(_joined_check) — 나간 뒤 다시 들어오는 것이 정상 흐름이고,
--   이력은 겹쳐 쓰지 않고 새 행으로 쌓인다. 이중 참여는 여전히 막힌다.
CREATE TABLE participation (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    room_id            BINARY(16)  NOT NULL,
    member_id          BINARY(16)  NOT NULL,
    participation_role VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    joined_at          DATETIME(6) NOT NULL,
    left_at            DATETIME(6) NULL,
    left_by_member_id  BINARY(16)  NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted_at         DATETIME(6) NULL,
    _joined_check BOOLEAN GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL AND status = 'JOINED' THEN TRUE ELSE NULL END
    ),
    PRIMARY KEY (id),
    CONSTRAINT uk_participation_room_member_joined UNIQUE (room_id, member_id, _joined_check)
);
CREATE INDEX ix_participation_member_id ON participation (member_id);

-- 각 참가 신청에 어떤 이력서를 냈는가. V1은 불변 원본 파일 키와 메타데이터를 제출 시점 값으로 보존한다.
-- 철회 후 재신청은 새 신청과 새 제출 행을 만든다. 철회해도 기존 제출은 지우지 않아 신청 당시 기록을 유지한다.
-- deleted_at 은 운영이 잘못 생성된 제출을 무효화할 때만 사용한다.
CREATE TABLE resume_submission (
    id                  BIGINT     NOT NULL AUTO_INCREMENT,
    room_application_id BIGINT     NOT NULL,
    room_id             BINARY(16) NOT NULL,
    member_id           BINARY(16) NOT NULL,
    source_resume_id BINARY(16)   NOT NULL,
    file_key        VARCHAR(500) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    submitted_at        DATETIME(6) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    deleted_at          DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_resume_submission_room_application UNIQUE (room_application_id)
);
CREATE INDEX ix_resume_submission_member_id ON resume_submission (member_id);

-- ── 면접 진행 ───────────────────────────────────────────────────────────

-- 진행 확정 이후에만 생성된다(룸과 선택적 1:1).
-- 베이스 상속: 확정이 철회되면 계획을 지우지만, 방장이 짜 둔 오프닝·클로징 시간은 재확정 때 되살려 준다.
--   유니크에 _active_check 가 붙어 있어 재확정은 새 행으로 들어간다. 딸린 라운드도 함께 소프트 삭제한다.
CREATE TABLE interview_plan (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    room_id          BINARY(16) NOT NULL,
    opening_minutes  SMALLINT   NOT NULL,
    closing_minutes  SMALLINT   NOT NULL,
    last_assigned_at DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    deleted_at       DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_plan_room_active UNIQUE (room_id, _active_check)
);

-- 한 사람의 면접 = 한 라운드.
-- interviewee_member_id 가 NULL 일 수 있다 — 확정 후 취소로 자리가 비는 상태를 표현한다.
--   불참·취소가 나도 타임라인을 자동으로 바꾸지 않고 방장이 정리할 때까지 기다린다.
-- 「공정하게 배정하기」는 라운드를 지우고 새로 만들지 않고 갱신한다(upsert).
--   라운드 식별자가 바뀌면 여기에 매달린 피드백이 끊어지기 때문이다.
-- 예상 시각은 저장하지 않는다 — 룸 시작 시각 + 오프닝 + 앞선 라운드들의 시간으로 계산한다.
-- 베이스 상속: 방장의 「라운드 삭제」는 소프트 삭제다.
--   유니크가 (계획, seq)라 라운드를 지우면 뒤 라운드들이 앞으로 당겨져 seq 를 물려받는데,
--   _active_check 가 유니크를 살아있는 행끼리만 걸어 주므로 재정렬이 충돌하지 않는다.
--   지워진 라운드가 남아 있으면 "누구 면접이 잡혔다 빠졌는가"를 되짚을 수 있다
--   (「룸 진행 준비」 4.3 "그 사람의 라운드를 삭제한다 — 남은 기록도 함께 정리").
--   딸린 배정·피드백도 같은 시각으로 함께 소프트 삭제한다.
CREATE TABLE interview_round (
    id                    BIGINT     NOT NULL AUTO_INCREMENT,
    interview_plan_id     BIGINT     NOT NULL,
    seq                   SMALLINT   NOT NULL,
    interviewee_member_id BINARY(16) NULL,
    interview_minutes     SMALLINT   NOT NULL,
    feedback_minutes      SMALLINT   NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    deleted_at            DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_interview_round_plan_seq_active UNIQUE (interview_plan_id, seq, _active_check)
);

-- 라운드별 면접관·관찰자 배정. 면접자는 라운드의 컬럼이라 여기 들어가지 않는다.
-- 관찰자를 "면접자도 면접관도 아닌 사람"으로 계산하지 않고 명시적으로 배정하는 이유는,
-- 불참자를 타임라인에서 빼는 조작(「타임라인에서 제외하기」)을 표현하기 위해서다.
-- 베이스 상속: 「면접관·관찰자 포함·제외」는 소프트 삭제와 되살리기다.
--   유니크가 (라운드, 회원)이라 제외했다 다시 포함할 때 지워진 행이 자리를 막는데,
--   _active_check 가 그 충돌을 없앤다. 누가 언제 배정에서 빠졌는지가 불참 정리의 근거로 남는다.
--   「배정하기」는 라운드를 지우고 새로 만들지 않고 갱신하므로 죽은 행이 쌓이지 않는다.
CREATE TABLE round_assignment (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    interview_round_id BIGINT      NOT NULL,
    member_id          BINARY(16)  NOT NULL,
    assignment_role    VARCHAR(20) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted_at         DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_round_assignment_round_member_active UNIQUE (interview_round_id, member_id, _active_check)
);
CREATE INDEX ix_round_assignment_member_id ON round_assignment (member_id);

-- 구두 피드백 단계의 산출물. 면접관·관찰자의 최종 피드백과 면접자의 자가 피드백을 유형으로 구분한다.
-- 현재 진행 라운드는 별도 엔티티가 아니라 (룸, 면접자)로 식별하므로 피드백도 같은 키를 직접 가진다.
-- 베이스 상속: 작성자별 1건이다. 최종 피드백은 최초 등록만 허용하고 중복 등록을 거부한다.
--   자가 피드백은 같은 행의 본문을 수정할 수 있다. 삭제는 소프트로 남긴다.
-- disclosed_at 은 면접자가 최종 피드백 카드 하나를 열람 확인한 최초 시각이다. 재확인은 멱등이다.
--   룸 종료는 질문 메모 공개 조건일 뿐 이 가림막을 해제하지 않는다.
--   피드백은 신고 대상이 되는 텍스트라 운영이 내렸다가 되돌리는 경로가 필요하다.
CREATE TABLE round_feedback (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    room_id               BINARY(16)  NOT NULL,
    interviewee_member_id BINARY(16)  NOT NULL,
    author_member_id      BINARY(16)  NOT NULL,
    feedback_type         VARCHAR(20) NOT NULL,
    content               TEXT        NOT NULL,
    disclosed_at          DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    deleted_at            DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_round_feedback_round_author_active UNIQUE (room_id, interviewee_member_id, author_member_id, _active_check)
);

-- 질문은 라운드가 아니라 (룸, 대상 면접자)에 매단다.
-- 준비 단계에서 만들어져 라운드 편집을 넘나들며 살아남아야 하기 때문이다.
-- (피드백은 반대로 라운드가 진행되어야 생기는 산물이라 라운드에 매단다.)
-- 꼬리질문은 별도 테이블 없이 parent_question_id 자기 참조로 표현한다 — 구조가 동일하다.
-- 작성자가 불참으로 교체되어도 질문은 보존된다.
-- 베이스 상속: 「룸 진행 준비」 4.5 의 "남긴 질문은 삭제 후 재작성할 수 있다" 를 소프트 삭제로 구현한다.
--   물리 삭제할 수 없는 이유가 둘이다 — parent_question_id 로 매달린 꼬리질문이 고아가 되고,
--   진행 중 삭제하면 이미 쌓인 답변 요약·댓글·투표가 갈 곳을 잃는다.
CREATE TABLE question (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    room_id            BINARY(16)  NOT NULL,
    target_member_id   BINARY(16)  NOT NULL,
    author_member_id   BINARY(16)  NOT NULL,
    parent_question_id BIGINT      NULL,
    content            TEXT        NOT NULL,
    source             VARCHAR(20) NOT NULL,
    asked              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    deleted_at         DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_question_room_target ON question (room_id, target_member_id);
CREATE INDEX ix_question_parent_question_id ON question (parent_question_id);

-- 질문별 답변 요약. 면접관·관찰자 전원의 요약이 서로 공유된다.
-- 베이스 상속: 작성자별 1건이라 수정은 UPDATE 다. 질문이 소프트 삭제되면 요약도 함께 가린다 —
--   질문만 지우고 요약을 물리 삭제하면 되살릴 때 기록이 반쪽만 돌아온다. 재작성은 새 행이다.
CREATE TABLE answer_summary (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    question_id      BIGINT     NOT NULL,
    author_member_id BINARY(16) NOT NULL,
    content          TEXT       NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    deleted_at       DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_answer_summary_question_author_active UNIQUE (question_id, author_member_id, _active_check)
);

-- 질문별 코멘트. 한 사람이 한 질문에 여러 건 남길 수 있어 유니크를 걸지 않는다.
-- 베이스 상속: 작성자가 개별 삭제하고, 신고 대상이 되는 텍스트라 운영도 내릴 수 있어야 한다.
--   유니크가 없어 되살리기 충돌도 없다.
CREATE TABLE question_comment (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    question_id      BIGINT      NOT NULL,
    author_member_id BINARY(16)  NOT NULL,
    comment_type     VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    deleted_at       DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_question_comment_question_id ON question_comment (question_id);

-- 참여자별 클로징 제출. created_at 을 제출 시각으로 사용한다.
CREATE TABLE closing_response (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    room_id    BINARY(16)  NOT NULL,
    member_id  BINARY(16)  NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_closing_response_room_member_active UNIQUE (room_id, member_id, _active_check)
);

-- 클로징 제출에 종속된 원 질문 평가. 참여자와 유효 여부는 부모 closing_response 가 소유한다.
CREATE TABLE question_vote (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    closing_response_id BIGINT      NOT NULL,
    question_id         BIGINT      NOT NULL,
    vote                VARCHAR(20) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_question_vote_closing_response_question UNIQUE (closing_response_id, question_id)
);

-- 출석 기록. 진행 시작 시 확정 참여자별 출석·불참 선택을 한 번 저장하며 일반 사용자는 수정하지 않는다.
-- 라운드 배정과 무관하고, 시작 후 도착해도 최초에 기록된 불참 상태를 유지한다.
-- 관리자 정정은 현재 제품 범위 밖이다. 향후 운영 정책이 생기면 change_reason 을 사용한다.
-- deleted_at 은 그 사람이 애초에 해당 룸의 출석 대상이 아니었던 운영 예외에만 사용한다.
-- 출석은 신뢰 통계의 분모이므로 일반 흐름에서 물리 삭제하지 않는다.
CREATE TABLE attendance (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    room_id            BINARY(16)   NOT NULL,
    member_id          BINARY(16)   NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    change_reason      VARCHAR(200) NULL,
    recorder_member_id BINARY(16)   NOT NULL,
    recorded_at        DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    deleted_at         DATETIME(6)  NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_attendance_room_member_active UNIQUE (room_id, member_id, _active_check)
);
CREATE INDEX ix_attendance_member_id ON attendance (member_id);

-- ── 신뢰 ────────────────────────────────────────────────────────────────

-- 참여자 상호 평가. 신뢰 정보(완료 횟수·출석률·평균 별점 등)는 별도 테이블 없이
-- attendance 와 review 의 집계로 만든다 — 원천이 작아 조회 시점 집계로 충분하다.
-- visible_at = 제출 + 3시간. 그 전까지 수정·삭제 가능하고 그 뒤 대상자 노출 기준이 된다.
-- 실제 노출은 처리 주기에 따라 몇 분 늦을 수 있다.
-- 작성자 ≠ 대상자는 애플리케이션이 검증한다.
-- hidden_at: 신고 처리로 운영이 가린 시각(reported_at 과 짝). 작성자에게는 계속 보인다.
-- 베이스 상속: 「유저 후기」 4.3 의 "제출 후 3시간 동안 수정·삭제 허용" 을 소프트 삭제로 구현한다.
--   숨김과 삭제는 행위자도 효과도 다르므로 두 컬럼이 공존한다 —
--   hidden_at 은 운영이 남에게만 가리는 것이고, deleted_at 은 작성자가 통째로 거두는 것이다.
--   유니크에 _active_check 가 붙어 있어 삭제 후 재작성은 새 행으로 들어간다.
CREATE TABLE review (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    room_id          BINARY(16) NOT NULL,
    author_member_id BINARY(16) NOT NULL,
    target_member_id BINARY(16) NOT NULL,
    -- rolling deployment 호환용 레거시 컬럼. 구 core-api 태스크 종료 후 별도 contract migration으로 제거한다.
    rating           SMALLINT   NOT NULL DEFAULT 0,
    content          TEXT       NULL,
    meet_again       BOOLEAN    NULL,
    visible_at       DATETIME(6) NOT NULL,
    hidden_at        DATETIME(6) NULL,
    reported_at      DATETIME(6) NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    deleted_at       DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_review_room_author_target_active UNIQUE (room_id, author_member_id, target_member_id, _active_check)
);
CREATE INDEX ix_review_target_member_id ON review (target_member_id);

-- 평가 태그(다건). 태그는 열거값이며 마스터 테이블을 두지 않는다 —
-- "자주 받은 태그" 집계는 GROUP BY 로 되고, 마스터가 있어도 집계에 도움이 되지 않는다.
-- 마스터가 없는 단순 값이므로 값 컬렉션이다 — 이 스키마에서 @ElementCollection 은 여기 하나뿐이다.
-- 그래서 별도 PK 없이 쌍 유니크만 두고 타임스탬프도 deleted_at 도 없다.
-- 후기가 소프트 삭제되면 태그는 그대로 딸려 숨는다.
-- (member_profile_interest_* 는 양쪽이 다 엔티티인 M:N 이라 조인 테이블도 엔티티다. 위 정책 참고.)
CREATE TABLE review_tag (
    review_id BIGINT      NOT NULL,
    tag       VARCHAR(40) NOT NULL,
    CONSTRAINT uk_review_tag UNIQUE (review_id, tag)
);

-- 대상별 건너뛰기 퍼널 기록. 건너뛰기는 후기 작성 자격을 소모하는 상태가 아니므로
-- review 와 연결하거나 삭제하지 않는다. 같은 대상의 반복 동작은 첫 기록 하나로 멱등 처리한다.
-- 사용자 수정·삭제 대상이 아닌 append-only 사실이라 표준 소프트 삭제 베이스를 쓰지 않는다.
CREATE TABLE review_skip (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    room_id          BINARY(16)  NOT NULL,
    author_member_id BINARY(16)  NOT NULL,
    target_member_id BINARY(16)  NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_skip_room_author_target UNIQUE (room_id, author_member_id, target_member_id)
);

-- ── 채팅 ────────────────────────────────────────────────────────────────

-- 룸당 1개. 룸 생성 시 자동으로 만들어지고 룸의 참여자 구성을 그대로 따른다.
-- 룸과 완전한 1:1이지만 읽기 전용 전환·퇴장 처리 등 생명주기 규칙이 달라 테이블을 분리했다.
-- 베이스 상속: 「룸 채팅」 4.2 가 사용자의 개별 생성·삭제를 막고, 읽기 전용 전환은 read_only_at 이 갖는다.
--   deleted_at 은 룸이 내려갈 때 대화방도 함께 닫되 내용은 보존하기 위한 것이다.
CREATE TABLE chat_room (
    id           BIGINT     NOT NULL AUTO_INCREMENT,
    room_id      BINARY(16) NOT NULL,
    read_only_at DATETIME(6) NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    deleted_at   DATETIME(6) NULL,
    _active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_room_active UNIQUE (room_id, _active_check)
);

-- 텍스트 메시지만 지원한다. 사용자에게 수정·삭제를 제공하지 않는다(「룸 채팅」 5. Out of Scope).
-- 베이스 상속: 사용자가 못 지운다는 것과 운영이 못 지운다는 것은 다르다. 신고된 메시지를 가리는
--   경로가 없으면 대응할 방법이 DB 직접 삭제밖에 없는데, 그러면 대화 맥락이 통째로 사라진다.
-- 퇴장한 사용자의 메시지는 대화 맥락 유지를 위해 남기고 닉네임 옆에 (퇴장)을 표시한다.
-- author_member_id 가 NULL 이면 시스템 메시지(입장·퇴장 알림)다.
CREATE TABLE chat_message (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    chat_room_id     BIGINT      NOT NULL,
    author_member_id BINARY(16)  NULL,
    message_type     VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    deleted_at       DATETIME(6) NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_chat_message_chat_room_id ON chat_message (chat_room_id);

-- ── 스캐폴딩 ────────────────────────────────────────────────────────────

-- 외부 전달 전까지 보관하는 범용 내구성 작업 기록. 업무 데이터의 소프트 삭제 정책이 아니라
-- 전달 생명주기와 별도 보존 정책을 따르므로 deleted_at 없이 처리 완료 후 물리 삭제한다.
CREATE TABLE outbox (
    id           BINARY(16)   NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    relay_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    claim_token  VARCHAR(36)  NULL,
    lease_until  DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_outbox_created_at ON outbox (created_at);
CREATE INDEX ix_outbox_relay_status_created_at ON outbox (relay_status, created_at);

CREATE TABLE example_entity (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    example_column VARCHAR(255) NOT NULL,
    deleted_at     DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
