-- 자동 종료의 SYSTEM 주체를 기록한다(MOI-471).
-- API의 Flyway 적용 중에도 구버전 task는 handler_type 없는 INSERT를 보낼 수 있다.
-- 추가 시점부터 기본값을 두어 기존 행과 구버전 쓰기를 모두 MEMBER로 보존한다.
-- 롤백 호환 기간에는 이 기본값을 제거하지 않는다.
ALTER TABLE room_status_log
    ADD COLUMN handler_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER' AFTER transition_type;

-- MEMBER는 회원 ID를 갖고, SYSTEM은 NULL을 쓴다. 팩토리가 이 불변식을 보장한다.
ALTER TABLE room_status_log
    MODIFY COLUMN handler_member_id BINARY(16) NULL;
