-- 로컬 개발(docker-compose MySQL) 전용 테스트 데이터 — initdb 로만 로드된다.
-- 앱 설정(sql.init)에 태우지 않으므로 테스트(H2)에는 절대 들어가지 않는다. dev/live 에도 실행하지 않는다.
-- 테스트 회원 1명: 소개 작성 완료 상태이며 figma 목업 닉네임('집요한 수달 07')을 점유해
-- FE 가 닉네임 사용 불가(available=false) 플로우를 재현할 수 있다.

INSERT INTO member (id, email, nickname, status, role, last_login_at, created_at, updated_at)
VALUES (X'019daf00000070008000000000000101', 'localtest@moimyeon.dev', '집요한 수달 07', 'ACTIVE', 'ADMIN',
        '2026-07-01 00:00:00', '2026-07-01 00:00:00', '2026-07-01 00:00:00');

INSERT INTO social_account (provider, provider_id, linked_email, member_id, created_at, updated_at)
VALUES ('GOOGLE', 'local-test-sub', 'localtest@moimyeon.dev',
        X'019daf00000070008000000000000101', '2026-07-01 00:00:00', '2026-07-01 00:00:00');

INSERT INTO member_profile (id, member_id, bio, created_at, updated_at)
VALUES (X'019daf00000070008000000000000102', X'019daf00000070008000000000000101',
        '결제 도메인 3년 차 프론트엔드 개발자예요.',
        '2026-07-01 00:00:00', '2026-07-01 00:00:00');

-- 관심 직무: 프론트엔드(2)·서버·백엔드(1) — 값 컬렉션이 아니라 조인 엔티티라 타임스탬프를 채운다
INSERT INTO member_profile_interest_job_role (profile_id, job_role_id, created_at, updated_at)
VALUES (X'019daf00000070008000000000000102', 2, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
       (X'019daf00000070008000000000000102', 1, '2026-07-01 00:00:00', '2026-07-01 00:00:00');

-- 관심 회사: 달빛페이(1)·한빛커머스(2)
INSERT INTO member_profile_interest_company (profile_id, company_id, created_at, updated_at)
VALUES (X'019daf00000070008000000000000102', 1, '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
       (X'019daf00000070008000000000000102', 2, '2026-07-01 00:00:00', '2026-07-01 00:00:00');

INSERT INTO terms_agreement (id, member_id, terms_id, agreed_at, created_at, updated_at)
VALUES (X'019daf00000070008000000000000201', X'019daf00000070008000000000000101',
        X'019daf00000070008000000000000001', '2026-07-01 00:00:00', '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
       (X'019daf00000070008000000000000202', X'019daf00000070008000000000000101',
        X'019daf00000070008000000000000002', '2026-07-01 00:00:00', '2026-07-01 00:00:00', '2026-07-01 00:00:00');
