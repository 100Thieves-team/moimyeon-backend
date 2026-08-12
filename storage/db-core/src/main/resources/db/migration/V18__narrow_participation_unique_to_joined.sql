-- 나간 뒤 같은 룸에 다시 참여하는 것은 정상 흐름이다(MOI-397 나가기·재신청).
-- 유니크가 상태를 보지 않으면 신청은 받아 놓고 방장이 수락하는 순간 무결성 위반으로 터진다.
-- 좁히는 방향이라 기존 행은 검사 대상에서 빠질 뿐 충돌하지 않는다.
ALTER TABLE participation
    DROP INDEX uk_participation_room_member_active;

ALTER TABLE participation
    DROP COLUMN _active_check;

ALTER TABLE participation
    ADD COLUMN _joined_check BOOLEAN GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL AND status = 'JOINED' THEN TRUE ELSE NULL END
    );

ALTER TABLE participation
    ADD CONSTRAINT uk_participation_room_member_joined UNIQUE (room_id, member_id, _joined_check);
