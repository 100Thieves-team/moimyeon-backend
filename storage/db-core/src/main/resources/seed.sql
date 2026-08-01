-- 공통 시드(참조 데이터): 현재 유효 약관. MVP 는 약관 발행 UI 없이 이 파일이 원천이다.
-- - local(H2): spring.sql.init 이 schema.sql 다음에 자동 실행
-- - 로컬 MySQL: docker-compose initdb 로 자동 실행
-- - dev/live: 수동 1회 실행으로 반영 (재실행 시 uk_terms_type_version 위반 실패 = 이미 반영된 상태)
-- 개정 절차: 새 버전 INSERT + 이전 버전 status='DEPRECATED' UPDATE 를 이 파일에 함께 추가한다.
-- id 는 BINARY(16) 고정 UUID (H2/MySQL 공통 문법인 hex 리터럴 사용).

-- 참조 데이터 최소 시드: 크롤러가 붙기 전 FE 드롭다운·검색 개발용.
-- 업무키(code, sigungu_code, corp_code) 기준이라 크롤러 적재와 충돌하지 않는다.
INSERT INTO sido (id, sido_code, name, short_name, is_metro, sort_order)
VALUES (1, '11', '서울특별시', '서울', TRUE, 1),
       (2, '41', '경기도', '경기', FALSE, 2);

INSERT INTO sigungu (id, sido_id, sigungu_code, name, level, sort_order)
VALUES (1, 1, '11680', '강남구', 'GU', 1),
       (2, 1, '11440', '마포구', 'GU', 2),
       (3, 1, '11110', '종로구', 'GU', 3),
       (4, 2, '41135', '성남시분당구', 'GU', 1);

INSERT INTO job_group (id, code, display_name, sort_order)
VALUES (1, 'IT_개발', 'IT·개발', 1);

INSERT INTO job_role (id, job_group_id, code, display_name, sort_order)
VALUES (1, 1, '서버_백엔드', '서버·백엔드', 1),
       (2, 1, '프론트엔드', '프론트엔드', 2),
       (3, 1, 'iOS', 'iOS', 3),
       (4, 1, '안드로이드', '안드로이드', 4),
       (5, 1, '데이터_엔지니어', '데이터 엔지니어', 5),
       (6, 1, 'PM_PO', 'PM·PO', 6);

INSERT INTO company (id, name_kr, name_normalized, verified)
VALUES (1, '달빛페이', '달빛페이', TRUE),
       (2, '한빛커머스', '한빛커머스', TRUE),
       (3, '구름클라우드', '구름클라우드', TRUE),
       (4, '별빛헬스', '별빛헬스', TRUE);

INSERT INTO terms (id, type, version, title, content, required, effective_from, status, created_at, updated_at)
VALUES (X'019daf00000070008000000000000001', 'SERVICE', 'v1.0', '모이면 이용약관',
        '제1조(목적) 이 약관은 모이면 서비스의 이용 조건과 절차를 규정합니다.',
        TRUE, '2026-07-01 00:00:00', 'ACTIVE', '2026-07-01 00:00:00', '2026-07-01 00:00:00'),
       (X'019daf00000070008000000000000002', 'PRIVACY', 'v1.0', '개인정보 처리방침',
        '모이면은 회원 가입과 서비스 제공을 위해 최소한의 개인정보를 수집·이용합니다.',
        TRUE, '2026-07-01 00:00:00', 'ACTIVE', '2026-07-01 00:00:00', '2026-07-01 00:00:00');
