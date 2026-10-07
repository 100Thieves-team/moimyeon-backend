-- 참가 신청 시 선택한 불변 이력서 파일 참조와 메타데이터를 제출 기록에 보존한다.
ALTER TABLE resume_submission
    RENAME COLUMN resume_id TO source_resume_id;

ALTER TABLE resume_submission
    ADD COLUMN file_key VARCHAR(500) NULL AFTER source_resume_id,
    ADD COLUMN original_name VARCHAR(255) NULL AFTER file_key,
    ADD COLUMN size_bytes BIGINT NULL AFTER original_name,
    ADD COLUMN content_type VARCHAR(100) NULL AFTER size_bytes;

-- MOI-379 배포 전에는 resume_submission 쓰기 경로가 없으므로 기존 행이 없어야 한다.
-- nullable 추가 후 NOT NULL 전환으로 배포 중 DDL 호환성을 확보한다.
ALTER TABLE resume_submission
    MODIFY COLUMN file_key VARCHAR(500) NOT NULL,
    MODIFY COLUMN original_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN size_bytes BIGINT NOT NULL,
    MODIFY COLUMN content_type VARCHAR(100) NOT NULL;
