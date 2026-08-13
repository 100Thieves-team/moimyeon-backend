-- MOI-449: 회원 프로필에서 진행 방식 선호와 선호 지역을 제거한다.
ALTER TABLE member_profile DROP COLUMN meeting_preference;
ALTER TABLE member_profile DROP COLUMN sigungu_id;
