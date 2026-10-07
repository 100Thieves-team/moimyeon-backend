-- 공개 전에 지워진 후기는 notified_at 이 영영 NULL 로 남는다. deleted_at 을 visible_at 앞에 두어 인덱스에서 거른다.
CREATE INDEX ix_review_notified_at_deleted_at_visible_at ON review (notified_at, deleted_at, visible_at);
