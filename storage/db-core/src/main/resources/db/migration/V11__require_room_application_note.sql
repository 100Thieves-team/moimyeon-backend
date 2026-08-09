-- 전달 사항은 선택 입력이지만 Application 경계부터 빈 문자열로 정규화한다.
-- 기존 NULL 행을 보정한 뒤 저장소에서도 같은 불변식을 보장한다.
UPDATE room_application
SET note = ''
WHERE note IS NULL;

ALTER TABLE room_application
    MODIFY COLUMN note TEXT NOT NULL;
