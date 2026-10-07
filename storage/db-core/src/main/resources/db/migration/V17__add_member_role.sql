-- 일반 회원과 운영 관리자는 같은 회원 신원을 사용하고 역할로 권한을 구분한다.
-- 기존 회원은 모두 일반 회원으로 이관한다.
ALTER TABLE member
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER status;
