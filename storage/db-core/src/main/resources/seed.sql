-- 공통 시드(참조 데이터): 현재 유효 약관. MVP 는 약관 발행 UI 없이 이 파일이 원천이다.
-- - local(H2): spring.sql.init 이 schema.sql 다음에 자동 실행
-- - 로컬 MySQL: docker-compose initdb 로 자동 실행
-- - dev/live: 수동 1회 실행으로 반영 (재실행 시 uk_terms_type_version 위반 실패 = 이미 반영된 상태)
-- 개정 절차: 새 버전 INSERT + 이전 버전 status='DEPRECATED' UPDATE 를 이 파일에 함께 추가한다.
-- id 는 BINARY(16) 고정 UUID (H2/MySQL 공통 문법인 hex 리터럴 사용).

INSERT INTO terms (id, type, version, title, content, required, effective_from, status, created_at, updated_at)
VALUES (X'019daf00000070008000000000000001', 'SERVICE', 'v1.0', '모이면 이용약관',
        '제1조(목적) 이 약관은 모이면 서비스의 이용 조건과 절차를 규정합니다.',
        TRUE, '2026-07-01 00:00:00', 'ACTIVE', '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
       (X'019daf00000070008000000000000002', 'PRIVACY', 'v1.0', '개인정보 처리방침',
        '모이면은 회원 가입과 서비스 제공을 위해 최소한의 개인정보를 수집·이용합니다.',
        TRUE, '2026-07-01 00:00:00', 'ACTIVE', '2026-07-01 00:00:00', '2026-07-01 00:00:00');
