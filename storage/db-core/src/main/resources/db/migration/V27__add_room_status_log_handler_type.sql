-- room_status_log 에 전이 주체(handler_type)를 더한다(MOI-471, MOI-396 이 넘긴 작업).
-- 8시간 자동 종료 배치가 COMPLETED 전이를 남길 때 넣을 회원이 없다 — handler_member_id 에
-- 방장을 채우면 "방장이 완료 처리했다"는 거짓 기록이 되고, 이 테이블은 분쟁 대응의 유일한 근거다.
--
-- 불변식: handler_type = 'MEMBER' 면 handler_member_id NOT NULL, 'SYSTEM' 이면 NULL.
-- DB 제약이 아니라 애플리케이션(RoomStatusLogEntity 팩토리 byMember/bySystem)이 지킨다.
--
-- 기존 행은 전부 MEMBER 로 백필한다 — 지금까지의 전이(CANCELED·CONFIRMED·IN_PROGRESS·COMPLETED)는
-- 모두 회원이 일으켰다.

ALTER TABLE room_status_log
    ADD COLUMN handler_type VARCHAR(20) NULL AFTER transition_type;

UPDATE room_status_log
SET handler_type = 'MEMBER'
WHERE handler_type IS NULL;

ALTER TABLE room_status_log
    MODIFY COLUMN handler_type VARCHAR(20) NOT NULL;

ALTER TABLE room_status_log
    MODIFY COLUMN handler_member_id BINARY(16) NULL;
