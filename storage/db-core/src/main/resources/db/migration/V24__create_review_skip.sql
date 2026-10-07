CREATE TABLE review_skip (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    room_id          BINARY(16)  NOT NULL,
    author_member_id BINARY(16)  NOT NULL,
    target_member_id BINARY(16)  NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_skip_room_author_target UNIQUE (room_id, author_member_id, target_member_id)
);
