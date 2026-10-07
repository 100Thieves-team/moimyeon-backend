-- 익명 선택 기능 이전의 후기는 작성자 정보를 노출하지 않았으므로 기존 행과 구 태스크의 신규 행을 익명으로 보존한다.
ALTER TABLE review
    ADD COLUMN anonymous BOOLEAN NOT NULL DEFAULT TRUE AFTER content;
