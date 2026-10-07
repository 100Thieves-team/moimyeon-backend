-- CONFIRMED -> RECRUITING -> CONFIRMED 재확정은 같은 전이 종류를 여러 번 기록한다.
-- 상태 자체의 멱등성은 잠근 room 행의 현재 상태로 지키고, 로그는 append-only 이력으로 둔다.
ALTER TABLE room_status_log
    DROP INDEX uk_room_status_log_room_transition_active;

ALTER TABLE room_status_log
    DROP COLUMN _active_check;

-- 모집/확정 전이는 반복할 수 있지만 종료 상태는 룸당 하나만 존재해야 한다.
ALTER TABLE room_status_log
    ADD COLUMN _terminal_check BOOLEAN GENERATED ALWAYS AS (
        CASE
            WHEN deleted_at IS NULL AND transition_type IN ('COMPLETED', 'CANCELED') THEN TRUE
            ELSE NULL
        END
    );

ALTER TABLE room_status_log
    ADD CONSTRAINT uk_room_status_log_room_terminal_active UNIQUE (room_id, _terminal_check);

CREATE INDEX ix_room_status_log_room_transition_occurred
    ON room_status_log (room_id, transition_type, occurred_at, id);
