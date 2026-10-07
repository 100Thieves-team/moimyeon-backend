-- V2 — 크롤러·카탈로그 테이블을 프로젝트 컨벤션에 맞춘다
--
-- 대상: sido · sigungu · job_group · job_role · company · job_posting · job_posting_role
-- 이 테이블들에는 크롤링 데이터가 들어 있다(2026-07-31 dev 기준 company 50,046 ·
-- job_posting 2,000 · job_posting_role 3,603 · job_role 296 · sigungu 230 · job_group 25 · sido 16).
--
-- 그래서 이 파일에는 DROP TABLE 도 TRUNCATE 도 DELETE 도 한 줄 없다. 전부 ALTER 다.
--   - RENAME INDEX / RENAME COLUMN 은 메타데이터만 바꾼다(ALGORITHM=INPLACE). 행이 움직이지 않는다.
--   - ADD COLUMN 은 MySQL 8 에서 INSTANT 다(맨 뒤 추가).
--   - sigungu.level 의 ENUM→VARCHAR 만 테이블 재작성인데, 230행이고 값('SI'/'GUN'/'GU')은 그대로 옮겨진다.
--
-- 크롤러 소유 FK 는 남긴다. 컨벤션의 "FK 를 걸지 않는다"는 앱이 참조 무결성을 코드로 검증한다는
-- 뜻이고 앱 테이블에 적용된다. 크롤러 테이블 사이의 FK 는 적재 파이프라인이 쓰는 장치다 —
-- 특히 job_posting_role 의 ON DELETE CASCADE 를 없애면 크롤러가 공고를 지울 때 매핑이 고아로 남는다.
--
-- ⚠️ 크롤러 코드 영향: job_posting.retired_at 이 deleted_at 으로 바뀐다.
--    카탈로그 5개 테이블은 이미 deleted_at 으로 통일되어 있었고 job_posting 만 남아 있었다.
--    이 마이그레이션 배포와 함께 크롤러 쪽 retired_at 참조를 deleted_at 으로 바꿔야 한다.

-- ── sido ────────────────────────────────────────────────────────────────
ALTER TABLE sido RENAME INDEX uq_sido_code TO uk_sido_sido_code;
ALTER TABLE sido RENAME INDEX uq_sido_name TO uk_sido_name;
ALTER TABLE sido RENAME INDEX uq_sido_short TO uk_sido_short_name;

-- ── sigungu ─────────────────────────────────────────────────────────────
-- level 을 VARCHAR 로 바꾸는 이유는 두 가지다. schema.sql 이 H2 와 공통 문법이어야 하고,
-- 엔티티가 @Enumerated(EnumType.STRING) 으로 매핑하므로 DB 쪽 ENUM 이 이득을 주지 않는다.
ALTER TABLE sigungu RENAME INDEX uq_sigungu_code TO uk_sigungu_sigungu_code;
ALTER TABLE sigungu RENAME INDEX uq_sigungu_sido_name TO uk_sigungu_sido_name;
ALTER TABLE sigungu RENAME INDEX ix_sigungu_sido TO ix_sigungu_sido_id;
ALTER TABLE sigungu MODIFY COLUMN level VARCHAR(10) NOT NULL COMMENT '단위구분(SI=시, GUN=군, GU=구)';

-- ── job_group ───────────────────────────────────────────────────────────
ALTER TABLE job_group RENAME INDEX uq_job_group_code TO uk_job_group_code;

-- ── job_role ────────────────────────────────────────────────────────────
ALTER TABLE job_role RENAME INDEX uq_job_role_code TO uk_job_role_code;
ALTER TABLE job_role RENAME INDEX ix_job_role_group TO ix_job_role_job_group_id;

-- ── company ─────────────────────────────────────────────────────────────
-- verified / created_by_member_id: 카탈로그에 없는 회사를 사용자가 룸 생성 중 등록하는 경로를 위한 것.
-- 앱이 만든 행은 verified = FALSE 로만 들어가고, TRUE 로 올리는 것은 크롤러·운영의 몫이다.
-- 기존 50,046 행은 크롤러가 넣은 것이므로 DEFAULT FALSE 로 들어가지만, 이후 크롤러 적재분과 함께
-- 검증 완료로 올리는 것은 별도 운영 절차로 다룬다(이 마이그레이션은 값을 건드리지 않는다).
ALTER TABLE company RENAME INDEX uq_company_corp_code TO uk_company_corp_code;
ALTER TABLE company RENAME INDEX ix_company_name_norm TO ix_company_name_normalized;
ALTER TABLE company
    ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '검증 완료 여부(앱 등록분은 FALSE)',
    ADD COLUMN created_by_member_id BINARY(16) NULL COMMENT '앱에서 등록한 회원(크롤러 적재분은 NULL)';

-- ── job_posting ─────────────────────────────────────────────────────────
ALTER TABLE job_posting RENAME COLUMN retired_at TO deleted_at;
ALTER TABLE job_posting RENAME INDEX uq_job_posting_src TO uk_job_posting_source_uid;
ALTER TABLE job_posting RENAME INDEX ix_posting_company TO ix_job_posting_company_id;
ALTER TABLE job_posting RENAME INDEX ix_posting_open TO ix_job_posting_is_open;
ALTER TABLE job_posting
    ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '검증 완료 여부(앱 등록분은 FALSE)',
    ADD COLUMN created_by_member_id BINARY(16) NULL COMMENT '앱에서 등록한 회원(크롤러 적재분은 NULL)';

-- ── job_posting_role ────────────────────────────────────────────────────
-- posting_id → job_posting_id: 참조 컬럼명은 <테이블>_id 라는 컨벤션에 맞춘다.
-- FK 가 이 컬럼을 참조하고 있으므로 끊고 → 이름 바꾸고 → 다시 건다.
-- 재부착 시 3,603 행을 검증 스캔하지만 데이터는 그대로다.
ALTER TABLE job_posting_role DROP FOREIGN KEY fk_ppr_posting;
ALTER TABLE job_posting_role DROP FOREIGN KEY fk_ppr_role;
ALTER TABLE job_posting_role RENAME COLUMN posting_id TO job_posting_id;
ALTER TABLE job_posting_role RENAME INDEX uq_posting_role TO uk_job_posting_role_posting_role;
ALTER TABLE job_posting_role RENAME INDEX ix_ppr_role TO ix_job_posting_role_job_role_id;
ALTER TABLE job_posting_role
    ADD CONSTRAINT fk_job_posting_role_job_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_posting (id) ON DELETE CASCADE;
ALTER TABLE job_posting_role
    ADD CONSTRAINT fk_job_posting_role_job_role
        FOREIGN KEY (job_role_id) REFERENCES job_role (id);
