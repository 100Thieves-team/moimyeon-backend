-- 사용자 요청으로 아직 동의되지 않은 예약 문서의 시행일을 10월 7일 0시(KST)로 정정한다.
-- 기존 동의가 생겼다면 동일 버전의 본문과 시행일을 바꾸지 않는다.
SET @terms_date_eligible := (
    SELECT COUNT(*)
    FROM terms t
    WHERE ((t.id = X'f614c0bb87b544e8baf12b8b2ad4c1b2' AND t.type = 'SERVICE')
        OR (t.id = X'd91d5a8f228a4a56874a88dd532febe7' AND t.type = 'PRIVACY'))
      AND t.version = 'v1.1'
      AND t.status = 'ACTIVE'
      AND t.deleted_at IS NULL
      AND t.effective_from = '2026-10-08 00:00:00'
      AND t.content LIKE '%시행일: 2026년 10월 8일%'
      AND NOT EXISTS (SELECT 1 FROM terms_agreement a WHERE a.terms_id = t.id)
);
SET @terms_date_guard := IF(
    @terms_date_eligible = 2,
    'SELECT 1',
    'SELECT 1 FROM ABORT_terms_v1_1_effective_date_requires_unagreed_terms'
);
PREPARE terms_date_guard_stmt FROM @terms_date_guard;
EXECUTE terms_date_guard_stmt;
DEALLOCATE PREPARE terms_date_guard_stmt;

UPDATE terms
SET effective_from = '2026-10-07 00:00:00',
    content = REPLACE(content, '2026년 10월 8일', '2026년 10월 7일'),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id IN (X'f614c0bb87b544e8baf12b8b2ad4c1b2', X'd91d5a8f228a4a56874a88dd532febe7')
  AND version = 'v1.1'
  AND status = 'ACTIVE'
  AND effective_from = '2026-10-08 00:00:00'
  AND deleted_at IS NULL;
