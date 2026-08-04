-- 철회 후 재신청하더라도 각 신청 당시의 제출 이력서를 정확히 보존하도록 신청과 제출을 직접 연결한다.
-- MOI-379의 철회 쓰기 경로가 배포되기 전이므로 기존 제출은 같은 룸·회원의 신청 하나와만 대응해야 한다.

SET @invalid_resume_submission_links := (
    SELECT COUNT(*)
    FROM resume_submission rs
    WHERE (
        SELECT COUNT(*)
        FROM room_application ra
        WHERE ra.room_id = rs.room_id
          AND ra.applicant_member_id = rs.member_id
    ) <> 1
);

SET @guard := IF(
    @invalid_resume_submission_links > 0,
    'SELECT 1 FROM ABORT_resume_submission_requires_exactly_one_room_application',
    'SELECT 1'
);

PREPARE guard_stmt FROM @guard;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

ALTER TABLE resume_submission
    ADD COLUMN room_application_id BIGINT NULL AFTER id;

UPDATE resume_submission rs
JOIN room_application ra
  ON ra.room_id = rs.room_id
 AND ra.applicant_member_id = rs.member_id
SET rs.room_application_id = ra.id;

ALTER TABLE resume_submission
    MODIFY COLUMN room_application_id BIGINT NOT NULL,
    DROP INDEX uk_resume_submission_room_member_active,
    DROP COLUMN _active_check,
    ADD CONSTRAINT uk_resume_submission_room_application UNIQUE (room_application_id);
