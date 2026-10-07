-- 알림 수신 설정(MOI-544). 기존 회원은 컬럼 기본값으로 채워진다.
ALTER TABLE member
    ADD COLUMN is_web_push_allowed        BOOLEAN     NOT NULL DEFAULT TRUE AFTER last_login_at,
    ADD COLUMN is_activity_email_enabled  BOOLEAN     NOT NULL DEFAULT TRUE AFTER is_web_push_allowed,
    ADD COLUMN is_marketing_email_agreed  BOOLEAN     NOT NULL DEFAULT FALSE AFTER is_activity_email_enabled,
    ADD COLUMN marketing_email_agreed_at  DATETIME(6) NULL AFTER is_marketing_email_agreed;
