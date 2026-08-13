-- 시간 배분 체감 수집을 폐기하고, 질문 평가를 참여자별 클로징 제출의 구성 요소로 묶는다.
-- 기존 created_at 은 클로징 제출 시각으로 그대로 사용한다.

-- 자식만 소프트 삭제된 기록은 부모 수명으로 안전하게 옮길 근거가 없다.
-- 임의 삭제나 되살리기 대신 마이그레이션을 중단하고 운영 결정을 요구한다.
SET @soft_deleted_question_votes := (SELECT COUNT(*) FROM question_vote WHERE deleted_at IS NOT NULL);
SET @guard := IF(
    @soft_deleted_question_votes > 0,
    'SELECT 1 FROM ABORT_question_vote_has_independent_deleted_rows',
    'SELECT 1'
);
PREPARE guard_stmt FROM @guard;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

-- 기존 평가는 질문의 룸과 투표자로 부모 제출을 정확히 하나 찾을 수 있어야 한다.
SET @ambiguous_question_votes := (
    SELECT COUNT(*)
    FROM (
        SELECT qv.id
        FROM question_vote qv
        JOIN question q ON q.id = qv.question_id
        LEFT JOIN closing_response cr
          ON cr.room_id = q.room_id
         AND cr.member_id = qv.voter_member_id
        GROUP BY qv.id
        HAVING COUNT(cr.id) <> 1
    ) invalid_question_vote
);
SET @guard := IF(
    @ambiguous_question_votes > 0,
    'SELECT 1 FROM ABORT_question_vote_owner_is_missing_or_ambiguous',
    'SELECT 1'
);
PREPARE guard_stmt FROM @guard;
EXECUTE guard_stmt;
DEALLOCATE PREPARE guard_stmt;

ALTER TABLE question_vote
    ADD COLUMN closing_response_id BIGINT NULL AFTER id;

UPDATE question_vote qv
JOIN question q ON q.id = qv.question_id
JOIN closing_response cr
  ON cr.room_id = q.room_id
 AND cr.member_id = qv.voter_member_id
SET qv.closing_response_id = cr.id;

ALTER TABLE question_vote
    DROP INDEX uk_question_vote_question_voter_active;

ALTER TABLE question_vote
    DROP COLUMN _active_check;

ALTER TABLE question_vote
    MODIFY COLUMN closing_response_id BIGINT NOT NULL,
    DROP COLUMN voter_member_id,
    DROP COLUMN deleted_at,
    ADD CONSTRAINT uk_question_vote_closing_response_question UNIQUE (closing_response_id, question_id);

ALTER TABLE closing_response
    DROP COLUMN pacing;
