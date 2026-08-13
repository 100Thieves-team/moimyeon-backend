ALTER TABLE round_feedback
    DROP INDEX uk_round_feedback_round_author_active;

ALTER TABLE round_feedback
    ADD COLUMN room_id BINARY(16) NULL AFTER id,
    ADD COLUMN interviewee_member_id BINARY(16) NULL AFTER room_id,
    ADD COLUMN disclosed_at DATETIME(6) NULL AFTER content;

UPDATE round_feedback rf
    JOIN interview_round ir ON ir.id = rf.interview_round_id
    JOIN interview_plan ip ON ip.id = ir.interview_plan_id
SET rf.room_id = ip.room_id,
    rf.interviewee_member_id = ir.interviewee_member_id;

ALTER TABLE round_feedback
    MODIFY COLUMN room_id BINARY(16) NOT NULL,
    MODIFY COLUMN interviewee_member_id BINARY(16) NOT NULL,
    DROP COLUMN interview_round_id,
    ADD CONSTRAINT uk_round_feedback_round_author_active
        UNIQUE (room_id, interviewee_member_id, author_member_id, _active_check);
