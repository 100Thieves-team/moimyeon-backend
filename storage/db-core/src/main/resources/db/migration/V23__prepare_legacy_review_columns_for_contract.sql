-- 새 core-api는 rating을 쓰지 않지만 rolling deployment 중인 구 태스크는 이 컬럼을 매핑한다.
-- 구 태스크가 모두 내려간 뒤 별도 contract migration에서 rating과 meet_again을 제거한다.
ALTER TABLE review ALTER COLUMN rating SET DEFAULT 0;
