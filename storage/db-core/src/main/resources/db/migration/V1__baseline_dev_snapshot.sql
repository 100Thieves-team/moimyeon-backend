-- V1 — dev 현재 상태 스냅샷 (baseline)
--
-- 이 파일은 2026-07-31 시점 dev RDS(moimyeondev) 의 실제 DDL 을 그대로 옮긴 것이다.
-- Flyway 마이그레이션 이력의 출발점을 기록하는 것이 목적이고, 두 가지로 쓰인다.
--
--   1. dev — 이미 이 상태이므로 실행되지 않는다.
--      spring.flyway.baseline-on-migrate=true 가 비어 있지 않은 스키마를 발견하면
--      V1 을 "이미 적용됨"으로 도장만 찍고(<< Flyway Baseline >>) V2 부터 실행한다.
--      크롤링 데이터(company 50,046 · job_posting 2,000 · job_posting_role 3,603 ·
--      job_role 296 · sigungu 230 · job_group 25 · sido 16)가 그대로 보존되는 이유가 이것이다.
--   2. 빈 DB(신규 환경·CI) — V1 부터 순서대로 실행되어 같은 결과에 도달한다.
--
-- 그래서 이 파일은 "우리가 원하는 스키마"가 아니라 "그때 거기 있던 스키마"다.
-- 컨벤션에 어긋나는 이름(uq_*)·타입(ENUM)·FK 가 그대로 들어 있는 것은 의도된 것이며,
-- 그것들을 바로잡는 일은 V2 가 한다. 이 파일은 앞으로 수정하지 않는다(체크섬 고정).
--
-- 마이그레이션은 MySQL 에서만 실행된다(로컬·테스트 H2 는 schema.sql 이 담당하고
-- spring.flyway.enabled=false 다). 따라서 여기서는 MySQL 방언을 그대로 쓴다.

CREATE TABLE IF NOT EXISTS `sido` (
    `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '시도 PK',
    `sido_code`  char(2)     NOT NULL COMMENT '법정동 시도코드(2자리, sigungu_code 앞2자리와 일치)',
    `name`       varchar(20) NOT NULL COMMENT '시도 정식명칭(예: 서울특별시)',
    `short_name` varchar(10) NOT NULL COMMENT '시도 축약명(공고·회사 지역문자열 조인키, 예: 서울)',
    `is_metro`   tinyint(1)  NOT NULL DEFAULT '0' COMMENT '서울·광역시 여부(1=예, 0=아님)',
    `sort_order` smallint    DEFAULT NULL COMMENT '정렬 순서',
    `created_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성시각',
    `updated_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정시각',
    `deleted_at` datetime    DEFAULT NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sido_code` (`sido_code`),
    UNIQUE KEY `uq_sido_name` (`name`),
    UNIQUE KEY `uq_sido_short` (`short_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지역-시도 마스터(법정동 2026)';

CREATE TABLE IF NOT EXISTS `sigungu` (
    `id`           bigint                    NOT NULL AUTO_INCREMENT COMMENT '시군구 PK',
    `sido_id`      bigint                    NOT NULL COMMENT '상위 시도 FK(sido.id)',
    `sigungu_code` char(5)                   DEFAULT NULL COMMENT '법정동 시군구코드(5자리)',
    `name`         varchar(40)               NOT NULL COMMENT '시군구/구 명칭(예: 종로구, 수원시)',
    `level`        enum('SI','GUN','GU')     NOT NULL COMMENT '단위구분(SI=시, GUN=군, GU=구)',
    `sort_order`   smallint                  DEFAULT NULL COMMENT '시도 내 정렬 순서',
    `created_at`   datetime                  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성시각',
    `updated_at`   datetime                  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정시각',
    `deleted_at`   datetime                  DEFAULT NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sigungu_sido_name` (`sido_id`,`name`),
    UNIQUE KEY `uq_sigungu_code` (`sigungu_code`),
    KEY `ix_sigungu_sido` (`sido_id`),
    CONSTRAINT `fk_sigungu_sido` FOREIGN KEY (`sido_id`) REFERENCES `sido` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지역-시군구 마스터(법정동 2026)';

CREATE TABLE IF NOT EXISTS `job_group` (
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '직군 PK',
    `code`         varchar(40) NOT NULL COMMENT '직군 코드(업무키, 예: IT_개발)',
    `display_name` varchar(60) NOT NULL COMMENT '직군 표시명(예: IT·개발)',
    `sort_order`   smallint    DEFAULT NULL COMMENT '정렬 순서',
    `created_at`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성시각',
    `updated_at`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정시각',
    `deleted_at`   datetime    DEFAULT NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_job_group_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='직무-직군 마스터(depth1)';

CREATE TABLE IF NOT EXISTS `job_role` (
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '직무 PK',
    `job_group_id` bigint      NOT NULL COMMENT '상위 직군 FK(job_group.id)',
    `code`         varchar(60) NOT NULL COMMENT '직무 코드(업무키·전역유니크, 예: 서버_백엔드)',
    `display_name` varchar(80) NOT NULL COMMENT '직무 표시명(예: 서버·백엔드)',
    `sort_order`   smallint    DEFAULT NULL COMMENT '직군 내 정렬 순서(원본 depth2_order)',
    `created_at`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성시각',
    `updated_at`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정시각',
    `deleted_at`   datetime    DEFAULT NULL COMMENT '소스 폐기 시각(크롤러 소유, 재크롤 미출현 시 세팅, NULL=유효)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_job_role_code` (`code`),
    KEY `ix_job_role_group` (`job_group_id`),
    CONSTRAINT `fk_job_role_group` FOREIGN KEY (`job_group_id`) REFERENCES `job_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='직무-직무 마스터(depth2)';

CREATE TABLE IF NOT EXISTS `company` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '회사 PK(이관 시 DuckDB company_id 보존)',
    `corp_code`       char(8)      DEFAULT NULL COMMENT 'DART 고유번호(골든키, 유니크)',
    `name_kr`         varchar(200) NOT NULL COMMENT '회사명(국문)',
    `name_normalized` varchar(200) DEFAULT NULL COMMENT '정규화 회사명(법인격·공백 제거, 이름매칭용)',
    `created_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성시각',
    `updated_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정시각',
    `deleted_at`      datetime     DEFAULT NULL COMMENT '재수집 시 폐기(retire)된 시각. NULL=유효 현행, 값 존재=폐기(구버전)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_company_corp_code` (`corp_code`),
    KEY `ix_company_name_norm` (`name_normalized`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회사 마스터(DART+zighang)';

CREATE TABLE IF NOT EXISTS `job_posting` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '공고 PK',
    `source_uid`     varchar(64)  NOT NULL COMMENT '출처 공고 UUID(직행 jd_id)',
    `company_id`     bigint       DEFAULT NULL COMMENT '회사 FK(company.id, 이름키 유일매칭, 미매칭 NULL)',
    `title`          varchar(300) NOT NULL COMMENT '공고 제목',
    `regions`        varchar(100) DEFAULT NULL COMMENT '근무지 시도 다중값(비정규화, 구분자 |)',
    `career_min`     smallint     DEFAULT NULL COMMENT '최소 경력(년)',
    `career_max`     smallint     DEFAULT NULL COMMENT '최대 경력(년)',
    `employee_types` varchar(60)  DEFAULT NULL COMMENT '고용형태 다중값(구분자 |)',
    `educations`     varchar(60)  DEFAULT NULL COMMENT '학력 다중값(구분자 |)',
    `posted_at`      datetime     DEFAULT NULL COMMENT '공고 게시일시(원본 created_at)',
    `end_date`       datetime     DEFAULT NULL COMMENT '마감일시(상시=NULL)',
    `deadline_type`  varchar(20)  DEFAULT NULL COMMENT '마감유형(상시/마감일 등)',
    `is_open`        tinyint(1)   DEFAULT NULL COMMENT '모집중 여부(파생: 마감 전=1)',
    `source_url`     varchar(400) DEFAULT NULL COMMENT '원본 URL',
    `redirect_url`   varchar(400) DEFAULT NULL COMMENT '원본 지원 URL(원티드 등)',
    `summary`        text         COMMENT '요약',
    `content`        mediumtext   COMMENT '본문 원문(평탄화 텍스트)',
    `created_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성시각',
    `updated_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '레코드 수정시각',
    `retired_at`     datetime     DEFAULT NULL COMMENT '재수집 시 폐기(retire)된 시각(NULL=유효 현행, 값=폐기 구버전)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_job_posting_src` (`source_uid`),
    KEY `ix_posting_company` (`company_id`),
    KEY `ix_posting_open` (`is_open`),
    FULLTEXT KEY `ft_content` (`content`),
    CONSTRAINT `fk_posting_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='채용공고(직행, 2026+)';

CREATE TABLE IF NOT EXISTS `job_posting_role` (
    `id`          bigint NOT NULL AUTO_INCREMENT COMMENT '공고-직무 매핑 PK',
    `job_role_id` bigint NOT NULL COMMENT '직무 FK(job_role.id)',
    `posting_id`  bigint NOT NULL COMMENT '공고 FK(job_posting.id)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_posting_role` (`posting_id`,`job_role_id`),
    KEY `ix_ppr_role` (`job_role_id`),
    CONSTRAINT `fk_ppr_posting` FOREIGN KEY (`posting_id`) REFERENCES `job_posting` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ppr_role` FOREIGN KEY (`job_role_id`) REFERENCES `job_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공고↔직무 매핑(job_cat2 정규화)';

CREATE TABLE IF NOT EXISTS `member` (
    `id`            binary(16)   NOT NULL,
    `email`         varchar(320) NOT NULL,
    `status`        varchar(20)  NOT NULL,
    `last_login_at` datetime     NOT NULL,
    `withdrawn_at`  datetime     DEFAULT NULL,
    `created_at`    datetime     NOT NULL,
    `updated_at`    datetime     NOT NULL,
    `nickname`      varchar(30)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_member_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `social_account` (
    `id`           bigint       NOT NULL AUTO_INCREMENT,
    `provider`     varchar(20)  NOT NULL,
    `provider_id`  varchar(100) NOT NULL,
    `linked_email` varchar(320) DEFAULT NULL,
    `member_id`    binary(16)   NOT NULL,
    `created_at`   datetime     NOT NULL,
    `updated_at`   datetime     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_social_account_provider_provider_id` (`provider`,`provider_id`),
    KEY `ix_social_account_member_id` (`member_id`),
    CONSTRAINT `fk_social_account_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `refresh_token` (
    `id`         bigint      NOT NULL AUTO_INCREMENT,
    `token_hash` varchar(64) NOT NULL,
    `member_id`  binary(16)  NOT NULL,
    `expires_at` datetime    NOT NULL,
    `revoked_at` datetime    DEFAULT NULL,
    `created_at` datetime    NOT NULL,
    `updated_at` datetime    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_token_token_hash` (`token_hash`),
    KEY `ix_refresh_token_member_id` (`member_id`),
    KEY `ix_refresh_token_expires_at` (`expires_at`),
    CONSTRAINT `fk_refresh_token_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `member_profile` (
    `member_id`          binary(16)   NOT NULL,
    `nickname`           varchar(30)  NOT NULL,
    `job_role_id`        bigint       DEFAULT NULL,
    `bio`                varchar(500) DEFAULT NULL,
    `meeting_preference` varchar(20)  DEFAULT NULL,
    `sigungu_id`         bigint       DEFAULT NULL,
    `created_at`         datetime     NOT NULL,
    `updated_at`         datetime     NOT NULL,
    PRIMARY KEY (`member_id`),
    UNIQUE KEY `uk_member_profile_nickname` (`nickname`),
    KEY `fk_member_profile_job_role` (`job_role_id`),
    KEY `fk_member_profile_sigungu` (`sigungu_id`),
    CONSTRAINT `fk_member_profile_job_role` FOREIGN KEY (`job_role_id`) REFERENCES `job_role` (`id`),
    CONSTRAINT `fk_member_profile_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_member_profile_sigungu` FOREIGN KEY (`sigungu_id`) REFERENCES `sigungu` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `member_profile_interest` (
    `member_id`  binary(16) NOT NULL,
    `company_id` bigint     NOT NULL,
    UNIQUE KEY `uk_member_profile_interest` (`member_id`,`company_id`),
    KEY `fk_member_profile_interest_company` (`company_id`),
    CONSTRAINT `fk_member_profile_interest_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`),
    CONSTRAINT `fk_member_profile_interest_profile` FOREIGN KEY (`member_id`) REFERENCES `member_profile` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `terms` (
    `id`             binary(16)   NOT NULL,
    `type`           varchar(20)  NOT NULL,
    `version`        varchar(20)  NOT NULL,
    `title`          varchar(200) NOT NULL,
    `content`        text         NOT NULL,
    `required`       tinyint(1)   NOT NULL,
    `effective_from` datetime     NOT NULL,
    `status`         varchar(20)  NOT NULL,
    `created_at`     datetime     NOT NULL,
    `updated_at`     datetime     NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_terms_type_version` (`type`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `terms_agreement` (
    `id`         binary(16) NOT NULL,
    `member_id`  binary(16) NOT NULL,
    `terms_id`   binary(16) NOT NULL,
    `agreed_at`  datetime   NOT NULL,
    `created_at` datetime   NOT NULL,
    `updated_at` datetime   NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_terms_agreement_member_terms` (`member_id`,`terms_id`),
    KEY `fk_terms_agreement_terms` (`terms_id`),
    KEY `ix_terms_agreement_member_id` (`member_id`),
    CONSTRAINT `fk_terms_agreement_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
    CONSTRAINT `fk_terms_agreement_terms` FOREIGN KEY (`terms_id`) REFERENCES `terms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `example_entity` (
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `example_column` varchar(255) NOT NULL,
    `deleted_at`     datetime     DEFAULT NULL,
    `created_at`     datetime     NOT NULL,
    `updated_at`     datetime     NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
