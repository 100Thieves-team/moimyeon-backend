-- 로컬 개발(docker-compose MySQL) 전용 테스트 데이터 — initdb 로만 로드된다.
-- 앱 설정(sql.init)에 태우지 않으므로 테스트(H2)에는 절대 들어가지 않는다. dev/live 에도 실행하지 않는다.
-- 테스트 회원 1명: 프로필 작성 완료 상태이며 figma 목업 닉네임('집요한 수달 07')을 점유해
-- FE 가 닉네임 중복(E1007)·사용 불가(available=false) 플로우를 재현할 수 있다.

INSERT INTO member (id, email, status, last_login_at, created_at, updated_at)
VALUES (X'019daf00000070008000000000000101', 'localtest@moimyeon.dev', 'ACTIVE',
        '2026-07-01 00:00:00', '2026-07-01 00:00:00', '2026-07-01 00:00:00');

INSERT INTO social_account (provider, provider_id, linked_email, member_id, created_at, updated_at)
VALUES ('GOOGLE', 'local-test-sub', 'localtest@moimyeon.dev',
        X'019daf00000070008000000000000101', '2026-07-01 00:00:00', '2026-07-01 00:00:00');

-- job_role_id=2(프론트엔드), sigungu_id=1(서울 강남구) — seed.sql 의 참조 데이터 id
INSERT INTO member_profile (member_id, nickname, job_role_id, bio, meeting_preference, sigungu_id, created_at, updated_at)
VALUES (X'019daf00000070008000000000000101', '집요한 수달 07', 2,
        '결제 도메인 3년 차 프론트엔드 개발자예요.', 'OFFLINE', 1,
        '2026-07-01 00:00:00', '2026-07-01 00:00:00');

-- 관심 회사: 달빛페이(1)·한빛커머스(2)
INSERT INTO member_profile_interest (member_id, company_id)
VALUES (X'019daf00000070008000000000000101', 1),
       (X'019daf00000070008000000000000101', 2);

INSERT INTO terms_agreement (id, member_id, terms_id, agreed_at, created_at, updated_at)
VALUES (X'019daf00000070008000000000000201', X'019daf00000070008000000000000101',
        X'019daf00000070008000000000000001', '2026-07-01 00:00:00', '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
       (X'019daf00000070008000000000000202', X'019daf00000070008000000000000101',
        X'019daf00000070008000000000000002', '2026-07-01 00:00:00', '2026-07-01 00:00:00', '2026-07-01 00:00:00');
