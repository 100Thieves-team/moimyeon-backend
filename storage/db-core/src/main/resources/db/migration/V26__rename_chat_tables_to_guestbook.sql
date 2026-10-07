-- V26 — 채팅 테이블을 방명록으로 정리한다 (MOI-461)
--
-- 2026-08-04 PRD 대개편이 실시간 채팅을 삭제하고 방명록(1-depth 게시판)으로 전환했다.
-- 두 테이블은 V4 가 만든 뒤 쓰는 코드가 없어 비어 있다 — "chat" 이름을 남기면
-- 실시간 채팅이 계획인 줄 알게 되므로 지금 정리한다. 컬럼 정리의 근거:
--   - read_only_at 제거: 읽기 전용은 room_status_log 의 종료·취소 전이 시각 + 24h 파생으로
--     판정한다. 파생값을 저장하지 않는다
--   - message_type 제거·author_member_id NOT NULL: 입퇴장 시스템 메시지 개념 폐기.
--     방명록 글은 전부 사용자 글이다

RENAME TABLE chat_room TO room_guestbook;
RENAME TABLE chat_message TO guestbook_post;

ALTER TABLE room_guestbook DROP COLUMN read_only_at;
ALTER TABLE room_guestbook RENAME INDEX uk_chat_room_room_active TO uk_room_guestbook_room_active;

ALTER TABLE guestbook_post RENAME COLUMN chat_room_id TO room_guestbook_id;
ALTER TABLE guestbook_post DROP COLUMN message_type;
ALTER TABLE guestbook_post MODIFY author_member_id BINARY(16) NOT NULL;
ALTER TABLE guestbook_post RENAME INDEX ix_chat_message_chat_room_id TO ix_guestbook_post_room_guestbook_id;
