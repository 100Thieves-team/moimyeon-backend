-- V2에서 컬럼을 추가할 때 기존 크롤러 회사는 DEFAULT FALSE로 남겨 두었다.
-- 앱 등록 회사는 created_by_member_id가 있으므로 제외하고, 기존·후속 크롤러 회사만 선택 가능하게 전환한다.
-- 조건부 UPDATE라 재실행해도 결과가 같으며 폐기 여부는 검색 시 deleted_at으로 별도 판정한다.
UPDATE company
SET verified = TRUE
WHERE created_by_member_id IS NULL
  AND verified = FALSE;
