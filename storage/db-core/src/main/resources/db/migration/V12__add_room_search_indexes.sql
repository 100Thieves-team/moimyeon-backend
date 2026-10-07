-- 룸 탐색 목록(MOI-383)의 정렬 2종을 받치는 복합 인덱스.
--
-- 지금 room 인덱스는 job_posting_id / job_role_id / sigungu_id 단일 3개뿐(V4)이라 목록 조회를
-- 받쳐 줄 것이 없다. 목록 쿼리는 status 로 좁히고 정렬 키로 정렬한 뒤 LIMIT 을 거는 형태다.
--
-- 정렬 키 뒤에 id 를 붙이는 이유는 커서 때문이다. 커서 비교가 (정렬 키, id) 복합이라
-- 인덱스도 같은 순서여야 동점 구간에서 seek 이 이어진다. id 가 없으면 동점 구간마다 정렬이 다시 붙는다.
--
-- deleted_at 은 넣지 않는다. NULL 이 대다수라 선택도가 없어 인덱스만 커진다.
-- 필터 컬럼(job_role_id, sigungu_id 등)도 넣지 않는다. 조합이 7종이라 다 받칠 수 없고,
-- 선택도가 높은 필터는 옵티마이저가 기존 단일 인덱스를 골라 결과 집합이 작아진다.
CREATE INDEX ix_room_status_start_at_id ON room (status, start_at, id);
CREATE INDEX ix_room_status_created_at_id ON room (status, created_at, id);

-- participation 에는 인덱스를 추가하지 않는다. "자리 남음" 판정의 상관 서브쿼리가 room_id 로 좁히는데,
-- uk_participation_room_member_active (room_id, member_id, _active_check) 가 room_id 를 선두 컬럼으로
-- 가져 그대로 쓰인다. room_application 의 대기 수 집계도 uk_room_application_room_pending_active 가 같은 역할을 한다.
