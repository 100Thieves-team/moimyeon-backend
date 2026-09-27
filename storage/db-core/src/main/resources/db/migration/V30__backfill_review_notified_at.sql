-- 배포 직후 과거 후기 알림이 한꺼번에 나가지 않게 기존 행을 모두 채운다(MOI-499).
-- 앱과 DB 의 시간대가 같다는 보장이 없어 NOW() 로 거르지 않는다.
UPDATE review
   SET notified_at = visible_at;
