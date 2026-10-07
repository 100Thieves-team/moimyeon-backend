-- 프로필을 "회원당 항상 하나 존재하는 것"으로 바꾸고, PK 를 member_id 에서 자체 UUID 로 옮긴다.
--
-- (1) 자체 PK + 이름 있는 유니크
--     member_id 를 PK 로 쓰면 동시 생성 충돌이 PRIMARY 로만 나와 제약명으로 판별할 수 없다.
--     그래서 번역을 쓰기 옆에 두지 못하고 부모(member) 행 락으로 직렬화해야 했고, profile 이
--     member 저장소를 직접 잡는 격벽 침범이 따라왔다.
--
-- (2) 미지정 필드를 null 대신 값으로
--     가입 시 빈 프로필이 함께 생기므로 "아직 안 채웠다"가 정상 상태다. 그것을 null 로 표현하면
--     모든 사용처가 null 분기를 떠안는다. 문자열은 빈 문자열, 만남 선호는 UNSPECIFIED 상태로 둔다.
--     sigungu_id 는 카탈로그 참조 id 라 빈 값도 상태도 만들 수 없어 null 을 유지한다.
--
-- (3) 관심 회사·직무 조인이 member_id 대신 profile_id 를 참조
--     프로필 없이 관심만 남는 상태가 스키마상 불가능해지고, 관심은 프로필 개념 안에서 완결된다.

ALTER TABLE member_profile
    ADD COLUMN id BINARY(16) NULL FIRST;

UPDATE member_profile SET id = UUID_TO_BIN(UUID()) WHERE id IS NULL;

ALTER TABLE member_profile
    DROP PRIMARY KEY,
    MODIFY COLUMN id BINARY(16) NOT NULL,
    ADD PRIMARY KEY (id),
    ADD CONSTRAINT uk_member_profile_member UNIQUE (member_id);

UPDATE member_profile SET bio = '' WHERE bio IS NULL;
UPDATE member_profile SET meeting_preference = 'UNSPECIFIED' WHERE meeting_preference IS NULL;

ALTER TABLE member_profile
    MODIFY COLUMN bio VARCHAR(500) NOT NULL DEFAULT '',
    MODIFY COLUMN meeting_preference VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED';

-- 기존 회원 중 프로필이 없는 사람에게 빈 프로필을 만들어 준다.
INSERT INTO member_profile (id, member_id, bio, meeting_preference, sigungu_id, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), m.id, '', 'UNSPECIFIED', NULL, NOW(), NOW()
FROM member m
         LEFT JOIN member_profile p ON p.member_id = m.id
WHERE p.id IS NULL;

ALTER TABLE member_profile_interest_company
    ADD COLUMN profile_id BINARY(16) NULL AFTER id;

UPDATE member_profile_interest_company i
    JOIN member_profile p ON p.member_id = i.member_id
SET i.profile_id = p.id;

ALTER TABLE member_profile_interest_company
    DROP INDEX uk_member_profile_interest_company_active,
    MODIFY COLUMN profile_id BINARY(16) NOT NULL,
    ADD CONSTRAINT uk_member_profile_interest_company_active UNIQUE (profile_id, company_id, _active_check),
    DROP COLUMN member_id;

ALTER TABLE member_profile_interest_job_role
    ADD COLUMN profile_id BINARY(16) NULL AFTER id;

UPDATE member_profile_interest_job_role i
    JOIN member_profile p ON p.member_id = i.member_id
SET i.profile_id = p.id;

ALTER TABLE member_profile_interest_job_role
    DROP INDEX uk_member_profile_interest_job_role_active,
    MODIFY COLUMN profile_id BINARY(16) NOT NULL,
    ADD CONSTRAINT uk_member_profile_interest_job_role_active UNIQUE (profile_id, job_role_id, _active_check),
    DROP COLUMN member_id;
