-- 공개된 후기의 알림 판정을 끝낸 시각(MOI-499).
-- DDL 은 암묵 커밋이라 백필(V30)·인덱스(V31)를 나눠 실패 시 재실행할 수 있게 한다.
ALTER TABLE review
    ADD COLUMN notified_at DATETIME(6) NULL AFTER reported_at;
